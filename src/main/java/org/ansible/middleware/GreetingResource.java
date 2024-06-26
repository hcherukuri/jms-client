package org.ansible.middleware;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.logging.Logger;
import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import java.util.logging.Level;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GreetingResource {
    private static final Logger log = Logger.getLogger(GreetingResource.class.getName());
    // Set up all the default values
    private static final String DEFAULT_CONNECTION_FACTORY = "jms/RemoteConnectionFactory";
    private static final String DEFAULT_DESTINATION = "jms/queue/test";
    private static final String DEFAULT_MESSAGE_COUNT = "1";
    private static final String DEFAULT_USERNAME = "quickstartUser";
    private static final String DEFAULT_PASSWORD = "quickstartPwd1!";
    private static final String INITIAL_CONTEXT_FACTORY = "org.wildfly.naming.client.WildFlyInitialContextFactory";
    @ConfigProperty(name = "greeting.wildfly_vm_ip", defaultValue="localhost")
    String wildflyVm;

    private volatile String flight_details;


    public String getLastPrice() {
        return flight_details;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public void hello(String message) {
        log.info(String.format("Wildfly VM IP: %s", wildflyVm));
        String userName = System.getProperty("username", DEFAULT_USERNAME);
        String password = System.getProperty("password", DEFAULT_PASSWORD);
        // Set up the namingContext for the JNDI lookup
        final Properties env = new Properties();
        env.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY);
        env.put(Context.PROVIDER_URL, System.getProperty(Context.PROVIDER_URL, "http-remoting://"+ wildflyVm + ":8080"));
        env.put(Context.SECURITY_PRINCIPAL, userName);
        env.put(Context.SECURITY_CREDENTIALS, password);

        Context namingContext = null;
        try {
            namingContext = new InitialContext(env);
            // Perform the JNDI lookups
            String connectionFactoryString = System.getProperty("connection.factory", DEFAULT_CONNECTION_FACTORY);
            log.log(Level.INFO, "Attempting to acquire connection factory \"{0}\"", connectionFactoryString);
            ConnectionFactory connectionFactory = (ConnectionFactory) namingContext.lookup(connectionFactoryString);
            log.log(Level.INFO, "Found connection factory \"{0}\" in JNDI", connectionFactoryString);

            String destinationString = System.getProperty("destination", DEFAULT_DESTINATION);
            log.log(Level.INFO, "Attempting to acquire destination \"{0}\"", destinationString);
            Destination destination = (Destination) namingContext.lookup(destinationString);
            log.log(Level.INFO, "Found destination \"{0}\" in JNDI", destinationString);

            int count = Integer.parseInt(System.getProperty("message.count", DEFAULT_MESSAGE_COUNT));
            String content = System.getProperty("message.content", message);

            try (JMSContext context = connectionFactory.createContext(userName, password)) {
                log.log(Level.INFO, "Sending {0} messages with content: {1}", new Object[]{count, content});
                // Send the specified number of messages
                context.createProducer().send(destination, content);

                // Create the JMS consumer
                JMSConsumer consumer = context.createConsumer(destination);
                // Then receive the same number of messages that were sent
                String text = consumer.receiveBody(String.class, 5000);
                log.log(Level.INFO, "Received message with content {0}", text);
                flight_details = text;

            }
        } catch (NamingException e) {
            e.printStackTrace();
            log.severe(e.getMessage());
        } finally {
            if (namingContext != null) {
                try {
                    namingContext.close();
                } catch (NamingException e) {
                    log.severe(e.getMessage());
                }
            }
        }
    }
}
