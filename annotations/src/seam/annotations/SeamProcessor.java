package seam.annotations;

import seam.annotations.Annotations.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;
import java.io.*;
import java.util.*;

@SupportedAnnotationTypes({
    "seam.annotations.Annotations.WorldStateSpec",
    "seam.annotations.Annotations.NetFacade",
    "seam.annotations.Annotations.SceneFacade",
    "seam.annotations.Annotations.EngineSpec"
})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class SeamProcessor extends AbstractProcessor{
    private static final String genPackage = "seam.gen";
    private final Set<String> generated = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round){
        if(round.processingOver()) return false;
        for(Element element : round.getElementsAnnotatedWith(WorldStateSpec.class)) generateWorldState(element);
        for(Element element : round.getElementsAnnotatedWith(NetFacade.class)) generateNetFacade(element);
        for(Element element : round.getElementsAnnotatedWith(SceneFacade.class)) generateSceneFacade(element);
        for(Element element : round.getElementsAnnotatedWith(EngineSpec.class)) generateEngine(element);
        return true;
    }

    private void generateWorldState(Element source){
        AnnotationMirror spec = mirror(source, WorldStateSpec.class);
        if(spec == null) return;
        String className = stringValue(spec, "className", "WorldStateGen");
        if(!generated.add(genPackage + "." + className)) return;

        record FieldEntry(String ownerFqn, String name, TypeMirror type, String initial){}
        record PayloadField(String name, TypeMirror type){}
        record PayloadEntry(String ownerFqn, String fieldName, String eventFqn, boolean isPublic, List<PayloadField> fields){}

        List<FieldEntry> fields = new ArrayList<>();
        Set<String> seenFields = new HashSet<>();

        for(TypeMirror ownerMirror : typeListValue(spec, "allPublicStatic")){
            TypeElement owner = asTypeElement(ownerMirror);
            if(owner == null){
                error(source, "Cannot resolve allPublicStatic type: " + ownerMirror);
                continue;
            }
            List<VariableElement> members = new ArrayList<>();
            for(Element member : owner.getEnclosedElements()){
                if(member.getKind() != ElementKind.FIELD) continue;
                VariableElement field = (VariableElement)member;
                Set<Modifier> mods = field.getModifiers();
                if(mods.contains(Modifier.PUBLIC) && mods.contains(Modifier.STATIC) && !mods.contains(Modifier.FINAL)){
                    members.add(field);
                }
            }
            members.sort(Comparator.comparing(f -> f.getSimpleName().toString()));
            for(VariableElement field : members){
                String key = owner.getQualifiedName() + "#" + field.getSimpleName();
                if(seenFields.add(key)){
                    fields.add(new FieldEntry(owner.getQualifiedName().toString(), field.getSimpleName().toString(), field.asType(), ""));
                }
            }
        }

        for(AnnotationValue groupValue : listValue(spec, "fields")){
            AnnotationMirror group = (AnnotationMirror)groupValue.getValue();
            TypeElement owner = asTypeElement(typeValue(group, "type"));
            if(owner == null){
                error(source, "Cannot resolve FieldGroup type.");
                continue;
            }
            List<String> names = stringListValue(group, "names");
            List<String> initials = stringListValue(group, "initial");
            if(!initials.isEmpty() && initials.size() != names.size()){
                error(source, "FieldGroup initial length must match names length on " + owner.getQualifiedName());
                continue;
            }
            for(int i = 0; i < names.size(); i++){
                String name = names.get(i);
                VariableElement field = findField(owner, name);
                if(field == null){
                    error(source, "Missing engine field: " + owner.getQualifiedName() + "#" + name);
                    continue;
                }
                if(!field.getModifiers().contains(Modifier.STATIC)){
                    error(source, "Expected static field: " + owner.getQualifiedName() + "#" + name);
                    continue;
                }
                if(!field.getModifiers().contains(Modifier.PUBLIC) || !isPubliclyAccessible(owner)){
                    error(source, "Direct WorldStateSpec field must be public: " + owner.getQualifiedName() + "#" + name);
                    continue;
                }
                String key = owner.getQualifiedName() + "#" + name;
                if(seenFields.add(key)){
                    fields.add(new FieldEntry(owner.getQualifiedName().toString(), name, field.asType(), initials.isEmpty() ? "" : initials.get(i)));
                }
            }
        }

        List<PayloadEntry> payloads = new ArrayList<>();
        for(TypeMirror hostMirror : typeListValue(spec, "payloadHosts")){
            TypeElement host = asTypeElement(hostMirror);
            if(host == null){
                error(source, "Cannot resolve payloadHost type: " + hostMirror);
                continue;
            }
            List<VariableElement> found = new ArrayList<>();
            for(Element member : host.getEnclosedElements()){
                if(member.getKind() != ElementKind.FIELD) continue;
                VariableElement field = (VariableElement)member;
                Set<Modifier> mods = field.getModifiers();
                if(!mods.contains(Modifier.STATIC) || !mods.contains(Modifier.FINAL)) continue;
                TypeElement fieldType = asTypeElement(field.asType());
                if(fieldType == null) continue;
                Element enclosing = fieldType.getEnclosingElement();
                if(enclosing instanceof TypeElement enc && enc.getQualifiedName().contentEquals("mindustry.game.EventType")){
                    found.add(field);
                }
            }
            found.sort(Comparator.comparing(f -> f.getSimpleName().toString()));
            for(VariableElement field : found){
                TypeElement eventType = asTypeElement(field.asType());
                List<PayloadField> eventFields = new ArrayList<>();
                for(Element member : eventType.getEnclosedElements()){
                    if(member.getKind() != ElementKind.FIELD) continue;
                    VariableElement ef = (VariableElement)member;
                    Set<Modifier> mods = ef.getModifiers();
                    if(mods.contains(Modifier.STATIC)) continue;
                    if(!mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.FINAL)){
                        error(source, "Unsupported event payload field: " + eventType.getQualifiedName() + "#" + ef.getSimpleName());
                        continue;
                    }
                    eventFields.add(new PayloadField(ef.getSimpleName().toString(), ef.asType()));
                }
                eventFields.sort(Comparator.comparing(PayloadField::name));
                boolean isPublic = field.getModifiers().contains(Modifier.PUBLIC) && isPubliclyAccessible(host);
                payloads.add(new PayloadEntry(host.getQualifiedName().toString(), field.getSimpleName().toString(), eventType.getQualifiedName().toString(), isPublic, eventFields));
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("package ").append(genPackage).append(";\n\n");
        out.append("import arc.struct.*;\n");
        out.append("import java.lang.reflect.*;\n\n");
        out.append("public final class ").append(className).append("{\n");

        for(int i = 0; i < payloads.size(); i++){
            PayloadEntry p = payloads.get(i);
            out.append("    private static final ").append(p.eventFqn).append(" livePayload").append(i).append(" = ");
            if(p.isPublic){
                out.append(p.ownerFqn).append('.').append(p.fieldName).append(";\n");
            }else{
                out.append("readStatic(").append(p.ownerFqn).append(".class, \"").append(p.fieldName).append("\");\n");
            }
        }
        out.append("\n");

        for(int i = 0; i < fields.size(); i++){
            FieldEntry f = fields.get(i);
            out.append("    private ").append(typeRef(f.type)).append(" f").append(i);
            if(!f.initial.isEmpty()) out.append(" = ").append(f.initial);
            out.append(";\n");
        }
        out.append("\n");

        for(int i = 0; i < payloads.size(); i++){
            PayloadEntry p = payloads.get(i);
            for(PayloadField pf : p.fields){
                out.append("    private ").append(typeRef(pf.type)).append(" p").append(i).append('_').append(pf.name).append(";\n");
            }
        }
        out.append("\n");

        out.append("    public static void registerFields(Seq<Field> target){\n");
        for(FieldEntry f : fields){
            out.append("        target.add(declaredField(").append(f.ownerFqn).append(".class, \"").append(f.name).append("\"));\n");
        }
        out.append("    }\n\n");

        out.append("    public static void registerContents(Seq<Field> target){\n");
        for(PayloadEntry p : payloads){
            out.append("        target.add(declaredField(").append(p.ownerFqn).append(".class, \"").append(p.fieldName).append("\"));\n");
        }
        out.append("    }\n\n");

        out.append("    public void captureFields(){\n");
        for(int i = 0; i < fields.size(); i++){
            FieldEntry f = fields.get(i);
            out.append("        f").append(i).append(" = ").append(f.ownerFqn).append('.').append(f.name).append(";\n");
        }
        out.append("    }\n\n");

        out.append("    public void installFields(){\n");
        for(int i = 0; i < fields.size(); i++){
            FieldEntry f = fields.get(i);
            out.append("        ").append(f.ownerFqn).append('.').append(f.name).append(" = f").append(i).append(";\n");
        }
        out.append("    }\n\n");

        out.append("    public void capturePayloads(){\n");
        for(int i = 0; i < payloads.size(); i++){
            PayloadEntry p = payloads.get(i);
            for(PayloadField pf : p.fields){
                out.append("        p").append(i).append('_').append(pf.name).append(" = livePayload").append(i).append('.').append(pf.name).append(";\n");
            }
        }
        out.append("    }\n\n");

        out.append("    public void installPayloads(){\n");
        for(int i = 0; i < payloads.size(); i++){
            PayloadEntry p = payloads.get(i);
            for(PayloadField pf : p.fields){
                out.append("        livePayload").append(i).append('.').append(pf.name).append(" = p").append(i).append('_').append(pf.name).append(";\n");
            }
        }
        out.append("    }\n\n");

        out.append("    public void release(){\n");
        for(int i = 0; i < fields.size(); i++){
            FieldEntry f = fields.get(i);
            if(f.type.getKind().isPrimitive()){
                out.append("        f").append(i).append(" = ").append(defaultPrimitive(f.type)).append(";\n");
            }else{
                out.append("        f").append(i).append(" = null;\n");
            }
        }
        for(int i = 0; i < payloads.size(); i++){
            PayloadEntry p = payloads.get(i);
            for(PayloadField pf : p.fields){
                if(pf.type.getKind().isPrimitive()){
                    out.append("        p").append(i).append('_').append(pf.name).append(" = ").append(defaultPrimitive(pf.type)).append(";\n");
                }else{
                    out.append("        p").append(i).append('_').append(pf.name).append(" = null;\n");
                }
            }
        }
        out.append("    }\n\n");

        out.append("    private static Field declaredField(Class<?> type, String name){\n");
        out.append("        try{\n");
        out.append("            Field field = type.getDeclaredField(name);\n");
        out.append("            field.setAccessible(true);\n");
        out.append("            return field;\n");
        out.append("        }catch(ReflectiveOperationException e){\n");
        out.append("            throw new ExceptionInInitializerError(e);\n");
        out.append("        }\n");
        out.append("    }\n\n");

        out.append("    @SuppressWarnings(\"unchecked\")\n");
        out.append("    private static <T> T readStatic(Class<?> type, String name){\n");
        out.append("        try{\n");
        out.append("            return (T)declaredField(type, name).get(null);\n");
        out.append("        }catch(IllegalAccessException e){\n");
        out.append("            throw new ExceptionInInitializerError(e);\n");
        out.append("        }\n");
        out.append("    }\n");
        out.append("}\n");

        writeSource(source, genPackage + "." + className, out.toString());
    }

    private void generateNetFacade(Element source){
        AnnotationMirror spec = mirror(source, NetFacade.class);
        if(spec == null) return;
        String className = stringValue(spec, "className", "LocalNetBase");
        if(!generated.add(genPackage + "." + className)) return;
        TypeElement target = asTypeElement(typeValue(spec, "target"));
        if(target == null){
            error(source, "Cannot resolve NetFacade target.");
            return;
        }
        Set<String> offlineFalse = new HashSet<>(stringListValue(spec, "offlineFalse"));
        Set<String> offlineNull = new HashSet<>(stringListValue(spec, "offlineNull"));
        Set<String> offlineEmpty = new HashSet<>(stringListValue(spec, "offlineEmpty"));
        Set<String> offlineDrop = new HashSet<>(stringListValue(spec, "offlineDrop"));

        List<ExecutableElement> methods = new ArrayList<>();
        for(Element member : target.getEnclosedElements()){
            if(member.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement)member;
            Set<Modifier> mods = method.getModifiers();
            if(!mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.STATIC) || mods.contains(Modifier.FINAL)) continue;
            methods.add(method);
        }
        methods.sort(Comparator.comparing((ExecutableElement m) -> m.getSimpleName().toString()).thenComparingInt(m -> m.getParameters().size()));

        StringBuilder out = new StringBuilder();
        out.append("package ").append(genPackage).append(";\n\n");
        out.append("import arc.struct.*;\n\n");
        out.append("public abstract class ").append(className).append(" extends ").append(target.getQualifiedName()).append("{\n");
        out.append("    private static final Iterable<mindustry.net.NetConnection> none = new Seq<>();\n");
        out.append("    protected final ").append(target.getQualifiedName()).append(" host;\n\n");
        out.append("    protected ").append(className).append("(").append(target.getQualifiedName()).append(" host){\n");
        out.append("        super(null);\n");
        out.append("        this.host = host;\n");
        out.append("    }\n\n");
        out.append("    public ").append(target.getQualifiedName()).append(" host(){\n        return host;\n    }\n\n");
        out.append("    protected abstract boolean local();\n\n");
        out.append("    protected abstract void requireHost();\n\n");

        for(ExecutableElement method : methods){
            String name = method.getSimpleName().toString();
            out.append("    @Override\n    public ");
            appendTypeParams(out, method.getTypeParameters());
            out.append(typeRef(method.getReturnType())).append(' ').append(name).append('(');
            StringBuilder args = new StringBuilder();
            List<? extends VariableElement> params = method.getParameters();
            for(int i = 0; i < params.size(); i++){
                if(i > 0){
                    out.append(", ");
                    args.append(", ");
                }
                VariableElement param = params.get(i);
                String pName = "p" + i;
                out.append(typeRef(param.asType())).append(' ').append(pName);
                args.append(pName);
            }
            out.append(')');
            appendThrows(out, method.getThrownTypes());
            out.append("{\n        ");
            boolean isVoid = method.getReturnType().getKind() == TypeKind.VOID;
            if(offlineFalse.contains(name)){
                out.append("return !local() && host.").append(name).append('(').append(args).append(");");
            }else if(offlineNull.contains(name)){
                out.append("return local() ? null : host.").append(name).append('(').append(args).append(");");
            }else if(offlineEmpty.contains(name)){
                out.append("return local() ? none : host.").append(name).append('(').append(args).append(");");
            }else if(offlineDrop.contains(name)){
                out.append("if(!local()) host.").append(name).append('(').append(args).append(");");
            }else{
                out.append("requireHost();\n        ");
                if(!isVoid) out.append("return ");
                out.append("host.").append(name).append('(').append(args).append(");");
            }
            out.append("\n    }\n\n");
        }
        out.append("}\n");

        writeSource(source, genPackage + "." + className, out.toString());
    }

    private void generateSceneFacade(Element source){
        AnnotationMirror spec = mirror(source, SceneFacade.class);
        if(spec == null) return;
        String className = stringValue(spec, "className", "WorldSceneBase");
        if(!generated.add(genPackage + "." + className)) return;
        TypeElement scene = asTypeElement(typeValue(spec, "scene"));
        TypeElement input = asTypeElement(typeValue(spec, "input"));
        if(scene == null || input == null){
            error(source, "Cannot resolve SceneFacade scene or input types.");
            return;
        }

        List<ExecutableElement> methods = new ArrayList<>();
        for(Element member : input.getEnclosedElements()){
            if(member.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement)member;
            Set<Modifier> mods = method.getModifiers();
            if(!mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.STATIC)) continue;
            methods.add(method);
        }
        methods.sort(Comparator.comparing(m -> m.getSimpleName().toString()));

        StringBuilder out = new StringBuilder();
        out.append("package ").append(genPackage).append(";\n\n");
        out.append("import arc.func.*;\n\n");
        out.append("public abstract class ").append(className).append(" extends ").append(scene.getQualifiedName()).append("{\n");
        out.append("    public abstract void run(Runnable task);\n\n");
        out.append("    public abstract <T> T get(Prov<T> task);\n\n");
        out.append("    @Override\n    public void act(float delta){\n        run(() -> super.act(delta));\n    }\n\n");

        for(ExecutableElement method : methods){
            String name = method.getSimpleName().toString();
            boolean isVoid = method.getReturnType().getKind() == TypeKind.VOID;
            out.append("    @Override\n    public ");
            out.append(typeRef(method.getReturnType())).append(' ').append(name).append('(');
            StringBuilder args = new StringBuilder();
            List<? extends VariableElement> params = method.getParameters();
            for(int i = 0; i < params.size(); i++){
                if(i > 0){
                    out.append(", ");
                    args.append(", ");
                }
                String pName = "p" + i;
                out.append(typeRef(params.get(i).asType())).append(' ').append(pName);
                args.append(pName);
            }
            out.append("){\n        ");
            if(isVoid){
                out.append("run(() -> super.").append(name).append('(').append(args).append("));");
            }else{
                out.append("return get(() -> super.").append(name).append('(').append(args).append("));");
            }
            out.append("\n    }\n\n");
        }
        out.append("}\n");

        writeSource(source, genPackage + "." + className, out.toString());
    }

    private void generateEngine(Element source){
        AnnotationMirror spec = mirror(source, EngineSpec.class);
        if(spec == null) return;
        String className = stringValue(spec, "className", "Engine");
        if(!generated.add(genPackage + "." + className)) return;

        StringBuilder holdersDecl = new StringBuilder();
        StringBuilder methodsDecl = new StringBuilder();
        Set<String> prefixes = new HashSet<>();

        for(AnnotationValue entryValue : listValue(spec, "classes")){
            AnnotationMirror entry = (AnnotationMirror)entryValue.getValue();
            String typeName = stringValue(entry, "typeName", "");
            TypeElement owner;
            if(!typeName.isEmpty()){
                owner = resolveByBinaryName(typeName);
                if(owner == null){
                    error(source, "Missing engine class: " + typeName);
                    continue;
                }
            }else{
                owner = asTypeElement(typeValue(entry, "type"));
                if(owner == null || owner.getQualifiedName().contentEquals("java.lang.Void")){
                    error(source, "ClassAccess requires type or typeName.");
                    continue;
                }
            }
            String prefix = stringValue(entry, "prefix", "");
            if(!prefixes.add(prefix)){
                error(source, "Duplicate ClassAccess prefix: " + prefix);
                continue;
            }
            String holder = "H_" + prefix;
            StringBuilder holderBody = new StringBuilder();
            boolean ownerPublic = isPubliclyAccessible(owner);
            String ownerExpr = ownerPublic ? owner.getQualifiedName() + ".class" : "loadType(\"" + binaryName(owner) + "\")";
            String receiverType = ownerPublic ? owner.getQualifiedName().toString() : publicSuperRef(owner);

            Set<String> readable = new LinkedHashSet<>(stringListValue(entry, "fields"));
            Set<String> writable = new LinkedHashSet<>(stringListValue(entry, "writable"));
            Set<String> setterOnly = new LinkedHashSet<>(stringListValue(entry, "setters"));
            readable.addAll(writable);
            Set<String> canWrite = new LinkedHashSet<>(writable);
            canWrite.addAll(setterOnly);
            Set<String> allFields = new LinkedHashSet<>(readable);
            allFields.addAll(canWrite);

            for(String name : allFields){
                VariableElement field = findField(owner, name);
                if(field == null){
                    error(source, "Missing engine field: " + owner.getQualifiedName() + "#" + name);
                    continue;
                }
                boolean isStatic = field.getModifiers().contains(Modifier.STATIC);
                String handle = "f_" + name;
                String handleRef = holder + "." + handle;
                holderBody.append("        static final Field ").append(handle).append(" = field(").append(ownerExpr).append(", \"").append(name).append("\");\n");

                TypeMirror fieldType = field.asType();
                String ref = safeTypeRef(fieldType);
                String cap = capitalize(name);
                String targetArg = isStatic ? "null" : "target";
                String targetParam = isStatic ? "" : receiverType + " target";

                if(readable.contains(name)){
                    if(!fieldType.getKind().isPrimitive()) methodsDecl.append("    @SuppressWarnings(\"unchecked\")\n");
                    methodsDecl.append("    public static ").append(ref).append(' ').append(prefix).append(cap).append('(').append(targetParam).append("){\n");
                    methodsDecl.append("        try{\n            return ");
                    appendFieldGet(methodsDecl, fieldType, ref, handleRef, targetArg);
                    methodsDecl.append(";\n        }catch(IllegalAccessException e){\n            throw new IllegalStateException(e);\n        }\n    }\n\n");
                }

                if(canWrite.contains(name)){
                    String params = isStatic ? ref + " value" : receiverType + " target, " + ref + " value";
                    methodsDecl.append("    public static void ").append(prefix).append("Set").append(cap).append('(').append(params).append("){\n");
                    methodsDecl.append("        try{\n            ");
                    appendFieldSet(methodsDecl, fieldType, handleRef, targetArg, "value");
                    methodsDecl.append(";\n        }catch(IllegalAccessException e){\n            throw new IllegalStateException(e);\n        }\n    }\n\n");
                }
            }

            for(String name : stringListValue(entry, "methods")){
                List<ExecutableElement> matched = findMethods(owner, name);
                if(matched.size() != 1){
                    error(source, matched.isEmpty() ? "Missing engine method: " + owner.getQualifiedName() + "#" + name : "Ambiguous overloaded engine method: " + owner.getQualifiedName() + "#" + name);
                    continue;
                }
                ExecutableElement method = matched.get(0);
                boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
                String handle = "m_" + name;
                String handleRef = holder + "." + handle;
                holderBody.append("        static final Method ").append(handle).append(" = method(").append(ownerExpr).append(", \"").append(name).append("\"");
                List<? extends VariableElement> params = method.getParameters();
                for(VariableElement p : params){
                    holderBody.append(", ").append(erasedClassExpr(p.asType()));
                }
                holderBody.append(");\n");

                TypeMirror retType = method.getReturnType();
                boolean isVoid = retType.getKind() == TypeKind.VOID;
                String retRef = safeTypeRef(retType);
                if(!isVoid && !retType.getKind().isPrimitive()) methodsDecl.append("    @SuppressWarnings(\"unchecked\")\n");
                methodsDecl.append("    public static ").append(retRef).append(' ').append(prefix).append(capitalize(name)).append('(');
                StringBuilder callArgs = new StringBuilder();
                boolean firstParam = true;
                if(!isStatic){
                    methodsDecl.append(receiverType).append(" target");
                    firstParam = false;
                }
                for(int i = 0; i < params.size(); i++){
                    if(!firstParam) methodsDecl.append(", ");
                    firstParam = false;
                    String pName = "p" + i;
                    methodsDecl.append(safeTypeRef(params.get(i).asType())).append(' ').append(pName);
                    if(i > 0) callArgs.append(", ");
                    callArgs.append(pName);
                }
                methodsDecl.append("){\n        ");
                String invoke = "invoke(" + handleRef + ", " + (isStatic ? "null" : "target") + (callArgs.isEmpty() ? "" : ", " + callArgs) + ")";
                if(isVoid){
                    methodsDecl.append(invoke).append(";\n");
                }else{
                    methodsDecl.append("return (").append(boxedRef(retType, retRef)).append(")").append(invoke).append(";\n");
                }
                methodsDecl.append("    }\n\n");
            }

            if(boolValue(entry, "constructor")){
                ExecutableElement ctor = findNoArgConstructor(owner);
                if(ctor == null){
                    error(source, "Missing no-arg constructor: " + owner.getQualifiedName());
                }else{
                    String handle = "c_ctor";
                    String handleRef = holder + "." + handle;
                    holderBody.append("        static final Constructor<?> ").append(handle).append(" = constructor(").append(ownerExpr).append(");\n");
                    methodsDecl.append("    public static ").append(receiverType).append(" new").append(capitalize(prefix)).append("(){\n");
                    methodsDecl.append("        return (").append(receiverType).append(")construct(").append(handleRef).append(");\n    }\n\n");
                }
            }

            if(!holderBody.isEmpty()){
                holdersDecl.append("    private static final class ").append(holder).append("{\n").append(holderBody).append("    }\n\n");
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("package ").append(genPackage).append(";\n\n");
        out.append("import java.lang.reflect.*;\n\n");
        out.append("public final class ").append(className).append("{\n");
        out.append("    private ").append(className).append("(){}\n\n");
        out.append(methodsDecl);
        out.append("    public static Class<?> initType(String name){\n");
        out.append("        try{\n            return Class.forName(name);\n");
        out.append("        }catch(ClassNotFoundException e){\n            throw new ExceptionInInitializerError(e);\n        }\n    }\n\n");
        out.append("    private static Class<?> loadType(String name){\n");
        out.append("        try{\n            return Class.forName(name, false, ").append(className).append(".class.getClassLoader());\n");
        out.append("        }catch(ClassNotFoundException e){\n            throw new ExceptionInInitializerError(e);\n        }\n    }\n\n");
        out.append("    public static Field field(Class<?> type, String name){\n");
        out.append("        try{\n            Field field = type.getDeclaredField(name);\n            field.setAccessible(true);\n            return field;\n");
        out.append("        }catch(ReflectiveOperationException e){\n            throw new ExceptionInInitializerError(e);\n        }\n    }\n\n");
        out.append("    public static Method method(Class<?> type, String name, Class<?>... parameters){\n");
        out.append("        try{\n            Method method = type.getDeclaredMethod(name, parameters);\n            method.setAccessible(true);\n            return method;\n");
        out.append("        }catch(ReflectiveOperationException e){\n            throw new ExceptionInInitializerError(e);\n        }\n    }\n\n");
        out.append("    private static Constructor<?> constructor(Class<?> type){\n");
        out.append("        try{\n            Constructor<?> ctor = type.getDeclaredConstructor();\n            ctor.setAccessible(true);\n            return ctor;\n");
        out.append("        }catch(ReflectiveOperationException e){\n            throw new ExceptionInInitializerError(e);\n        }\n    }\n\n");
        out.append("    private static Object construct(Constructor<?> ctor){\n");
        out.append("        try{\n            return ctor.newInstance();\n");
        out.append("        }catch(InvocationTargetException e){\n");
        out.append("            if(e.getCause() instanceof RuntimeException r) throw r;\n");
        out.append("            if(e.getCause() instanceof Error r) throw r;\n");
        out.append("            throw new RuntimeException(e.getCause());\n");
        out.append("        }catch(ReflectiveOperationException e){\n            throw new IllegalStateException(e);\n        }\n    }\n\n");
        out.append("    private static Object invoke(Method method, Object target, Object... args){\n");
        out.append("        try{\n            return method.invoke(target, args);\n");
        out.append("        }catch(InvocationTargetException e){\n");
        out.append("            if(e.getCause() instanceof RuntimeException r) throw r;\n");
        out.append("            if(e.getCause() instanceof Error r) throw r;\n");
        out.append("            throw new RuntimeException(e.getCause());\n");
        out.append("        }catch(IllegalAccessException e){\n            throw new IllegalStateException(e);\n        }\n    }\n\n");
        out.append(holdersDecl);
        out.append("}\n");

        writeSource(source, genPackage + "." + className, out.toString());
    }

    private static void appendFieldGet(StringBuilder out, TypeMirror type, String ref, String handle, String target){
        switch(type.getKind()){
            case BOOLEAN -> out.append(handle).append(".getBoolean(").append(target).append(')');
            case BYTE -> out.append(handle).append(".getByte(").append(target).append(')');
            case SHORT -> out.append(handle).append(".getShort(").append(target).append(')');
            case INT -> out.append(handle).append(".getInt(").append(target).append(')');
            case LONG -> out.append(handle).append(".getLong(").append(target).append(')');
            case CHAR -> out.append(handle).append(".getChar(").append(target).append(')');
            case FLOAT -> out.append(handle).append(".getFloat(").append(target).append(')');
            case DOUBLE -> out.append(handle).append(".getDouble(").append(target).append(')');
            default -> out.append('(').append(ref).append(')').append(handle).append(".get(").append(target).append(')');
        }
    }

    private static void appendFieldSet(StringBuilder out, TypeMirror type, String handle, String target, String value){
        switch(type.getKind()){
            case BOOLEAN -> out.append(handle).append(".setBoolean(").append(target).append(", ").append(value).append(')');
            case BYTE -> out.append(handle).append(".setByte(").append(target).append(", ").append(value).append(')');
            case SHORT -> out.append(handle).append(".setShort(").append(target).append(", ").append(value).append(')');
            case INT -> out.append(handle).append(".setInt(").append(target).append(", ").append(value).append(')');
            case LONG -> out.append(handle).append(".setLong(").append(target).append(", ").append(value).append(')');
            case CHAR -> out.append(handle).append(".setChar(").append(target).append(", ").append(value).append(')');
            case FLOAT -> out.append(handle).append(".setFloat(").append(target).append(", ").append(value).append(')');
            case DOUBLE -> out.append(handle).append(".setDouble(").append(target).append(", ").append(value).append(')');
            default -> out.append(handle).append(".set(").append(target).append(", ").append(value).append(')');
        }
    }

    private String erasedClassExpr(TypeMirror type){
        TypeMirror erased = processingEnv.getTypeUtils().erasure(type);
        return switch(erased.getKind()){
            case BOOLEAN -> "boolean.class";
            case BYTE -> "byte.class";
            case SHORT -> "short.class";
            case INT -> "int.class";
            case LONG -> "long.class";
            case CHAR -> "char.class";
            case FLOAT -> "float.class";
            case DOUBLE -> "double.class";
            case ARRAY -> {
                TypeMirror comp = ((ArrayType)erased).getComponentType();
                yield isPublicType(comp) ? safeTypeRef(erased) + ".class" : "Object[].class";
            }
            case DECLARED -> {
                TypeElement el = (TypeElement)((DeclaredType)erased).asElement();
                yield isPubliclyAccessible(el) ? el.getQualifiedName() + ".class" : "loadType(\"" + binaryName(el) + "\")";
            }
            default -> "Object.class";
        };
    }

    private String safeTypeRef(TypeMirror type){
        return switch(type.getKind()){
            case VOID -> "void";
            case BOOLEAN -> "boolean";
            case BYTE -> "byte";
            case SHORT -> "short";
            case INT -> "int";
            case LONG -> "long";
            case CHAR -> "char";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case ARRAY -> safeTypeRef(((ArrayType)type).getComponentType()) + "[]";
            case WILDCARD -> {
                WildcardType w = (WildcardType)type;
                if(w.getExtendsBound() != null && isPublicType(w.getExtendsBound())) yield "? extends " + safeTypeRef(w.getExtendsBound());
                if(w.getSuperBound() != null && isPublicType(w.getSuperBound())) yield "? super " + safeTypeRef(w.getSuperBound());
                yield "?";
            }
            case DECLARED -> {
                DeclaredType dt = (DeclaredType)type;
                TypeElement el = (TypeElement)dt.asElement();
                if(!isPubliclyAccessible(el)) yield publicSuperRef(el);
                String base = el.getQualifiedName().toString();
                List<? extends TypeMirror> args = dt.getTypeArguments();
                if(args.isEmpty()) yield base;
                StringBuilder sb = new StringBuilder(base).append('<');
                for(int i = 0; i < args.size(); i++){
                    if(i > 0) sb.append(", ");
                    sb.append(safeTypeRef(args.get(i)));
                }
                yield sb.append('>').toString();
            }
            default -> "Object";
        };
    }

    private String publicSuperRef(TypeElement el){
        TypeMirror sup = el.getSuperclass();
        while(sup != null && sup.getKind() == TypeKind.DECLARED){
            TypeElement superEl = (TypeElement)((DeclaredType)sup).asElement();
            if(isPubliclyAccessible(superEl)) return superEl.getQualifiedName().toString();
            sup = superEl.getSuperclass();
        }
        return "Object";
    }

    private boolean isPublicType(TypeMirror type){
        return switch(type.getKind()){
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID -> true;
            case ARRAY -> isPublicType(((ArrayType)type).getComponentType());
            case DECLARED -> isPubliclyAccessible((TypeElement)((DeclaredType)type).asElement());
            default -> false;
        };
    }

    private boolean isPubliclyAccessible(TypeElement el){
        for(Element cur = el; cur instanceof TypeElement type; cur = cur.getEnclosingElement()){
            if(!type.getModifiers().contains(Modifier.PUBLIC)) return false;
        }
        return true;
    }

    private static String boxedRef(TypeMirror type, String ref){
        return switch(type.getKind()){
            case BOOLEAN -> "Boolean";
            case BYTE -> "Byte";
            case SHORT -> "Short";
            case INT -> "Integer";
            case LONG -> "Long";
            case CHAR -> "Character";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            default -> ref;
        };
    }

    private static String capitalize(String s){
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private VariableElement findField(TypeElement target, String name){
        for(Element enclosed : target.getEnclosedElements()){
            if(enclosed.getKind() == ElementKind.FIELD && enclosed.getSimpleName().contentEquals(name)){
                return (VariableElement)enclosed;
            }
        }
        return null;
    }

    private List<ExecutableElement> findMethods(TypeElement target, String name){
        List<ExecutableElement> out = new ArrayList<>();
        for(Element enclosed : target.getEnclosedElements()){
            if(enclosed.getKind() == ElementKind.METHOD && enclosed.getSimpleName().contentEquals(name)){
                out.add((ExecutableElement)enclosed);
            }
        }
        return out;
    }

    private ExecutableElement findNoArgConstructor(TypeElement target){
        for(Element enclosed : target.getEnclosedElements()){
            if(enclosed.getKind() == ElementKind.CONSTRUCTOR && ((ExecutableElement)enclosed).getParameters().isEmpty()){
                return (ExecutableElement)enclosed;
            }
        }
        return null;
    }

    private TypeElement resolveByBinaryName(String binary){
        var elements = processingEnv.getElementUtils();
        TypeElement direct = elements.getTypeElement(binary.replace('$', '.'));
        if(direct != null) return direct;
        int dollar = binary.indexOf('$');
        if(dollar < 0) return null;
        TypeElement current = elements.getTypeElement(binary.substring(0, dollar));
        if(current == null) return null;
        for(String part : binary.substring(dollar + 1).split("\\$")){
            TypeElement next = null;
            for(Element enclosed : current.getEnclosedElements()){
                if(enclosed instanceof TypeElement nested && nested.getSimpleName().contentEquals(part)){
                    next = nested;
                    break;
                }
            }
            if(next == null) return null;
            current = next;
        }
        return current;
    }

    private String binaryName(TypeElement type){
        return processingEnv.getElementUtils().getBinaryName(type).toString();
    }

    private String typeRef(TypeMirror type){
        return switch(type.getKind()){
            case VOID -> "void";
            case BOOLEAN -> "boolean";
            case BYTE -> "byte";
            case SHORT -> "short";
            case INT -> "int";
            case LONG -> "long";
            case CHAR -> "char";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case ARRAY -> typeRef(((ArrayType)type).getComponentType()) + "[]";
            case TYPEVAR -> ((TypeVariable)type).asElement().getSimpleName().toString();
            case WILDCARD -> {
                WildcardType w = (WildcardType)type;
                if(w.getExtendsBound() != null) yield "? extends " + typeRef(w.getExtendsBound());
                if(w.getSuperBound() != null) yield "? super " + typeRef(w.getSuperBound());
                yield "?";
            }
            case DECLARED -> {
                DeclaredType dt = (DeclaredType)type;
                String base = ((TypeElement)dt.asElement()).getQualifiedName().toString();
                List<? extends TypeMirror> args = dt.getTypeArguments();
                if(args.isEmpty()) yield base;
                StringBuilder sb = new StringBuilder(base).append('<');
                for(int i = 0; i < args.size(); i++){
                    if(i > 0) sb.append(", ");
                    sb.append(typeRef(args.get(i)));
                }
                yield sb.append('>').toString();
            }
            default -> type.toString();
        };
    }

    private void appendTypeParams(StringBuilder out, List<? extends TypeParameterElement> params){
        if(params.isEmpty()) return;
        out.append('<');
        for(int i = 0; i < params.size(); i++){
            if(i > 0) out.append(", ");
            TypeParameterElement p = params.get(i);
            out.append(p.getSimpleName());
            List<? extends TypeMirror> bounds = p.getBounds();
            if(!(bounds.size() == 1 && bounds.get(0).toString().equals("java.lang.Object"))){
                out.append(" extends ");
                for(int j = 0; j < bounds.size(); j++){
                    if(j > 0) out.append(" & ");
                    out.append(typeRef(bounds.get(j)));
                }
            }
        }
        out.append("> ");
    }

    private void appendThrows(StringBuilder out, List<? extends TypeMirror> thrown){
        if(thrown.isEmpty()) return;
        out.append(" throws ");
        for(int i = 0; i < thrown.size(); i++){
            if(i > 0) out.append(", ");
            out.append(typeRef(thrown.get(i)));
        }
    }

    private static String defaultPrimitive(TypeMirror type){
        return switch(type.getKind()){
            case BOOLEAN -> "false";
            case LONG -> "0L";
            case FLOAT -> "0f";
            case DOUBLE -> "0d";
            default -> "0";
        };
    }

    private TypeElement asTypeElement(TypeMirror type){
        if(type instanceof DeclaredType declared && declared.asElement() instanceof TypeElement element) return element;
        return null;
    }

    private AnnotationMirror mirror(Element element, Class<?> annotation){
        String fqn = annotation.getCanonicalName();
        for(AnnotationMirror mirror : element.getAnnotationMirrors()){
            if(((TypeElement)mirror.getAnnotationType().asElement()).getQualifiedName().contentEquals(fqn)){
                return mirror;
            }
        }
        return null;
    }

    private Object rawValue(AnnotationMirror mirror, String name){
        for(var entry : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()){
            if(entry.getKey().getSimpleName().contentEquals(name)) return entry.getValue().getValue();
        }
        return null;
    }

    private String stringValue(AnnotationMirror mirror, String name, String fallback){
        Object value = rawValue(mirror, name);
        return value instanceof String text ? text : fallback;
    }

    private boolean boolValue(AnnotationMirror mirror, String name){
        Object value = rawValue(mirror, name);
        return value instanceof Boolean flag && flag;
    }

    private TypeMirror typeValue(AnnotationMirror mirror, String name){
        Object value = rawValue(mirror, name);
        return value instanceof TypeMirror type ? type : null;
    }

    @SuppressWarnings("unchecked")
    private List<AnnotationValue> listValue(AnnotationMirror mirror, String name){
        Object value = rawValue(mirror, name);
        return value instanceof List<?> list ? (List<AnnotationValue>)list : List.of();
    }

    private List<TypeMirror> typeListValue(AnnotationMirror mirror, String name){
        List<TypeMirror> out = new ArrayList<>();
        for(AnnotationValue entry : listValue(mirror, name)){
            if(entry.getValue() instanceof TypeMirror type) out.add(type);
        }
        return out;
    }

    private List<String> stringListValue(AnnotationMirror mirror, String name){
        List<String> out = new ArrayList<>();
        for(AnnotationValue entry : listValue(mirror, name)){
            if(entry.getValue() instanceof String text) out.add(text);
        }
        return out;
    }

    private void writeSource(Element origin, String fqn, String content){
        try{
            JavaFileObject file = processingEnv.getFiler().createSourceFile(fqn, origin);
            try(Writer writer = file.openWriter()){
                writer.write(content);
            }
        }catch(IOException error){
            error(origin, "Failed to generate " + fqn + ": " + error.getMessage());
        }
    }

    private void error(Element element, String message){
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
