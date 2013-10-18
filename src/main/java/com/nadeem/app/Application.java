package com.nadeem.app;

import com.nadeem.app.support.ApplicationContext;
import com.nadeem.app.support.ThreadStaller;

public class Application {

    private static final ThreadStaller applicationStaller  = new ThreadStaller();
    private static final ThreadStaller shutdownStaller     = new ThreadStaller();

    public static void main(final String[] args)
    {
        try {
            ApplicationContext.start();
            waitForTermination();

        } catch(Exception e) {
        	e.printStackTrace();
        } finally {
            ApplicationContext.shutdown();
            shutdownStaller.unstall(); // Allow the shutdown thread to resume, after which the VM will terminate.
        }
    }

    private static void waitForTermination() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Application.quit();
                shutdownStaller.stall(); // Don't finish executing this shutdown thread until the application has finished shutting down and tells it to.
            }
        });
        applicationStaller.stall(); // Pause the main() thread.  The shutdown hook registered above will resume execution when it's time to shutdown.
    }

    public static void quit() {
        applicationStaller.unstall();
    }

    public static String getMainContextFileLocation()
    {
        return ApplicationContext.MAIN_CONTEXT_FILE_LOCATION;
    }

    private Application()
    {
        throw new AssertionError("no instances of this class ever!");
    }
}