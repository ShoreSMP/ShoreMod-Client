"""Resolve visual sampler and dropped-item hooks for each target's actual rendering backend."""
import json
import os
from pathlib import Path
import re
import subprocess


def method_bodies(classpath, name):
    text = subprocess.check_output(["javap", "-p", "-s", "-c", "-classpath", classpath, name], text=True)
    matches = list(re.finditer(r"^  [^\s][^\n]*?\b([\w$]+)\([^\n]*?\)(?: throws [^;]+)?;\n    descriptor: (\S+)", text, re.M))
    return [(match[1], match[2], text[match.end():matches[i+1].start() if i+1 < len(matches) else len(text)]) for i, match in enumerate(matches)]


def single(values, label):
    if len(values) != 1:
        raise ValueError(f"Expected one {label}, found {values}")
    return values[0]


def set_selector(source, handler, method, target=None):
    position = source.index(" " + handler + "(")
    begin = source.rfind("\n    @", 0, position)
    end = source.index("\n", begin + 1)
    annotation = source[begin:end]
    annotation, count = re.subn(r'method\s*=\s*"[^"]*"', 'method=' + json.dumps(method), annotation)
    assert count == 1, annotation
    if target:
        annotation, count = re.subn(r'target\s*=\s*"[^"]*"', 'target=' + json.dumps(target), annotation)
        assert count == 1, annotation
    return source[:begin] + annotation + source[end:]


def resolve(root, target, stage):
    modern = target.startswith("26.")
    directory = Path(root) / "fabric/src/main/java/net/shoresmp/fabric"
    mixins = directory / "mixin"
    time = mixins / "VisualTimeMixin.java"
    if stage == "prepare":
        if modern:
            # 26.x samples the dimension's clock through its manager or instance.
            # The finalizer resolves the correct call after dependency resolution.
            time.write_text("""package net.shoresmp.fabric.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.timeline.AttributeTrackSampler;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.shoresmp.fabric.ShoreWorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(AttributeTrackSampler.class)
public abstract class VisualTimeMixin {
    @Shadow @Final private Holder<WorldClock> clock;
    @ModifyExpressionValue(method="applyTimeBased", at=@At(value="INVOKE", target="Lnet/minecraft/world/clock/ClockInstance;totalTicks()J"))
    private long shore$visualTime(long ticks) {
        return clock.is(WorldClocks.OVERWORLD) ? ShoreWorldView.visualTime(ticks) : ticks;
    }
}
""", encoding="utf-8")
        else:
            time.write_text("""package net.shoresmp.fabric.mixin;
import net.minecraft.world.dimension.DimensionType;
import net.shoresmp.fabric.ShoreWorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(DimensionType.class)
public abstract class VisualTimeMixin {
    @ModifyVariable(method="getSkyAngle", at=@At("HEAD"), argsOnly=true)
    private long shore$visualTime(long ticks) { return ShoreWorldView.visualTime(ticks); }
}
""", encoding="utf-8")
        return

    cp = os.pathsep.join(json.loads((Path(root) / "fabric/build/compat-classpath.json").read_text()))
    if modern:
        sampler = method_bodies(cp, "net.minecraft.world.timeline.AttributeTrackSampler")
        name, desc, body = single([m for m in sampler if m[0] == "applyTimeBased"], "attribute track sampler")
        clock = single(re.findall(r"(?:InterfaceMethod|Method) (net/minecraft/world/clock/[\w$]+)\.([\w$]+):(\([^)]*\)J)", body), "visual clock")
        value = time.read_text(encoding="utf-8")
        time.write_text(set_selector(value, "shore$visualTime", name + desc, "L" + clock[0] + ";" + clock[1] + clock[2]), encoding="utf-8")
    else:
        dim = method_bodies(cp, "net.minecraft.world.dimension.DimensionType")
        name, desc, _ = single([m for m in dim if m[1] == "(J)F" and "angle" in m[0].lower()], "legacy sky angle")
        time.write_text(set_selector(time.read_text(encoding="utf-8"), "shore$visualTime", name + desc), encoding="utf-8")

    weather_class = "net.minecraft.client.renderer.WeatherEffectRenderer" if modern else "net.minecraft.client.render.WeatherRendering"
    weather = []
    for name, desc, body in method_bodies(cp, weather_class):
        if "VertexConsumer;" not in desc or "Ljava/util/List;" not in desc:
            continue
        color = re.findall(r"Method ([\w/$]+)\.([\w$]+):(\(F\)I)", body)
        if color:
            owner, method, descriptor = single(color, "weather vertex color")
            weather.append((name + desc, "L" + owner + ";" + method + descriptor))
    weather_file = mixins / "WeatherTintMixin.java"
    weather_file.write_text(set_selector(weather_file.read_text(encoding="utf-8"), "shore$precipitationColor", *single(weather, "precipitation draw method")), encoding="utf-8")

    renderer = "net.minecraft.client.renderer.entity.ItemEntityRenderer" if modern else "net.minecraft.client.render.entity.ItemEntityRenderer"
    methods = method_bodies(cp, renderer)
    render = single([m for m in methods if re.match(r"\(L[^;]+/ItemEntityRenderState;L[^;]+/(PoseStack|MatrixStack);", m[1])], "dropped-item draw")
    update = single([m for m in methods if re.match(r"\(L[^;]+/ItemEntity;L[^;]+/ItemEntityRenderState;F\)V", m[1])], "dropped-item extraction")
    stack_call = single(re.findall(r"invokestatic[^\n]+// Method (?:[\w/$]+\.)?([\w$]+):(\(L[^;]+/(?:PoseStack|MatrixStack);[^\n ]*\)V)", render[2]), "dropped stack submission")
    renderer_source = mixins / "DroppedItemMixin.java"
    value = renderer_source.read_text(encoding="utf-8")
    value = set_selector(value, "shore$extractDrop", update[0] + update[1])
    value = set_selector(value, "shore$drawDrop", render[0] + render[1], "L" + renderer.replace(".", "/") + ";" + stack_call[0] + stack_call[1])
    if "getModelBoundingBox:" not in render[2]:
        value = re.sub(r"        var bounds = [^\n]+\n        float lift = [^\n]+",
                       "        float lift = pose.shore$block() ? 0.25f : (settled ? 0.035f : 0.17f);", value)
    if modern:
        pose = method_bodies(cp, "com.mojang.blaze3d.vertex.PoseStack")
        rotate = single([m[0] for m in pose if m[1] == "(Lorg/joml/Quaternionfc;)V"], "item rotation")
        value = re.sub(r"matrices\.\w+\(shore\$rotation", "matrices." + rotate + "(shore$rotation", value)
    renderer_source.write_text(value, encoding="utf-8")
    print("Resolved local sky, precipitation tint and item physics for", target)


if "stage" in globals():
    resolve(root, target, stage)
