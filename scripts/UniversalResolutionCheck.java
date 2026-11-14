package net.shoresmp.tests;
import net.fabricmc.loader.impl.discovery.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.impl.metadata.*;
/** Uses the real Loader solver on the exact packaged release metadata. */
public final class UniversalResolutionCheck {
 private static final Map<String,ModCandidateImpl> children=new HashMap<>();
 private static Object call(String name,ModCandidateImpl receiver,Class<?>[] types,Object...args)throws Exception{var method=ModCandidateImpl.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(receiver,args);}
 private static ModCandidateImpl plain(List<Path> paths,LoaderModMetadata meta,boolean remap,Collection<ModCandidateImpl> nested)throws Exception{return (ModCandidateImpl)call("createPlain",null,new Class<?>[]{List.class,LoaderModMetadata.class,boolean.class,Collection.class},paths,meta,remap,nested);}
 private static ModCandidateImpl nested(String path,long hash,LoaderModMetadata meta,boolean remap,Collection<ModCandidateImpl> nested)throws Exception{return (ModCandidateImpl)call("createNested",null,new Class<?>[]{String.class,long.class,LoaderModMetadata.class,boolean.class,Collection.class},path,hash,meta,remap,nested);}

 private static LoaderModMetadata metadata(byte[] bytes)throws Exception{
  return ModMetadataParser.parseMetadata(new ByteArrayInputStream(bytes),"release",List.of(),new VersionOverrides(),new DependencyOverrides(Path.of("build/resolver-config")),false);
 }
 private static ModCandidateImpl read(byte[] data,String name,boolean root)throws Exception{
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  if(!root&&children.containsKey(hash))return children.get(hash);
  var entries=new LinkedHashMap<String,byte[]>();
  try(var zip=new ZipInputStream(new ByteArrayInputStream(data))){for(ZipEntry e;(e=zip.getNextEntry())!=null;)entries.put(e.getName(),zip.readAllBytes());}
  var meta=metadata(entries.get("fabric.mod.json"));var nested=new ArrayList<ModCandidateImpl>();
  for(var e:entries.entrySet())if(e.getKey().startsWith("META-INF/jars/")&&e.getKey().endsWith(".jar"))nested.add(read(e.getValue(),e.getKey(),false));
  var result=root?plain(List.of(Path.of(name)),meta,false,nested):nested(name,0,meta,false,nested);
  if(!root)children.put(hash,result);
  for(var child:nested)call("addParent",child,new Class<?>[]{ModCandidateImpl.class},result);
  return result;
 }
 private static ModCandidateImpl platform(String id,String version)throws Exception{
  String json="{\"schemaVersion\":1,\"id\":\""+id+"\",\"version\":\""+version+"\"}";
  return plain(List.of(Path.of(id)),metadata(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)),false,List.of());
 }
 private static void flatten(ModCandidateImpl c,Set<ModCandidateImpl> all)throws Exception{if(all.add(c))for(var child:c.getNestedMods()){call("updateMinNestLevel",child,new Class<?>[]{ModCandidateImpl.class},c);flatten(child,all);}}
 public static void main(String[] args)throws Exception{
  for(int i=1;i<args.length;i+=2){
   children.clear();String target=args[i];var root=read(Files.readAllBytes(Path.of(args[0])),args[0],true);
   var all=Collections.newSetFromMap(new IdentityHashMap<ModCandidateImpl,Boolean>());flatten(root,all);
   all.add(platform("minecraft",target));all.add(platform("java",target.startsWith("26.")?"25":"21"));
   all.add(platform("fabricloader",System.getProperty("shoremod.testLoader","0.20.1")));all.add(platform("fabric-api",args[i+1]));
   var resolved=ModResolver.resolve(all,EnvType.CLIENT,Map.of());var selected=resolved.stream().filter(m->m.getId().equals("shoremod")).toList();
   if(selected.size()!=1||!selected.get(0).getVersion().getFriendlyString().equals(root.getVersion().getFriendlyString()+"-mc."+target))throw new AssertionError("Wrong implementation for "+target+": "+selected);
   if(resolved.stream().anyMatch(m->Set.of("rinku","mcef").contains(m.getId())))throw new AssertionError("Removed dependency selected");
   var gameDep=root.getDependencies().stream().filter(d->d.getModId().equals("minecraft")).findFirst().orElseThrow();
   if(gameDep.matches(Version.parse("1.21.9")))throw new AssertionError("Untested version accepted");
   System.out.println("PASS: real Fabric resolver selects exactly "+target);
  }
 }
}
