import java.nio.file.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

/** Parses all Java sources without resolving dependencies; not a substitute for Fabric compilation. */
public class SourceSyntaxCheck {
    public static void main(String[] args) throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var files=Files.walk(Path.of("."));var manager=compiler.getStandardFileManager(diagnostics,null,null)) {
            var sources=files.filter(p->p.toString().endsWith(".java")&&!p.toString().contains("build")).toList();
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,java.util.List.of("-proc:none","--release","21"),null,manager.getJavaFileObjectsFromPaths(sources));
            task.parse().forEach(tree->{});
            for(var diagnostic:diagnostics.getDiagnostics())System.out.println(diagnostic);
            if(diagnostics.getDiagnostics().stream().anyMatch(d->d.getKind()==Diagnostic.Kind.ERROR))System.exit(1);
            System.out.println("Parsed "+sources.size()+" Java sources successfully (syntax only)");
        }
    }
}
