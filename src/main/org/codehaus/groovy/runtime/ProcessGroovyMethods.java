/*
 * Copyright 2003-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.runtime;

import groovy.lang.Closure;
import groovy.lang.GroovyRuntimeException;

import java.io.*;

/**
 * This class defines new groovy methods which appear on normal JDK
 * classes related to process management.
 * <p/>
 * Static methods are used with the first parameter being the destination class,
 * i.e. <code>public static String reverse(String self)</code>
 * provides a <code>reverse()</code> method for <code>String</code>.
 * <p>
 * NOTE: While this class contains many 'public' static methods, it is
 * primarily regarded as an internal class (its internal package name
 * suggests this also). We value backwards compatibility of these
 * methods when used within Groovy but value less backwards compatibility
 * at the Java method call level. I.e. future versions of Groovy may
 * remove or move a method call in this file but would normally
 * aim to keep the method available from within Groovy.
 */
public class ProcessGroovyMethods extends DefaultGroovyMethodsSupport {

    /**
     * An alias method so that a process appears similar to System.out, System.in, System.err;
     * you can use process.in, process.out, process.err in a similar fashion.
     *
     * @param self a Process instance
     * @return the InputStream for the process
     * @since 1.0
     */
    public static InputStream getIn(Process self) {
        return self.getInputStream();
    }

    /**
     * Read the text of the output stream of the Process.
     *
     * @param self a Process instance
     * @return the text of the output
     * @throws java.io.IOException if an IOException occurs.
     * @since 1.0
     */
    public static String getText(Process self) throws IOException {
        return DefaultGroovyMethods.getText(new BufferedReader(new InputStreamReader(self.getInputStream())));
    }

    /**
     * An alias method so that a process appears similar to System.out, System.in, System.err;
     * you can use process.in, process.out, process.err in a similar fashion.
     *
     * @param self a Process instance
     * @return the error InputStream for the process
     * @since 1.0
     */
    public static InputStream getErr(Process self) {
        return self.getErrorStream();
    }

    /**
     * An alias method so that a process appears similar to System.out, System.in, System.err;
     * you can use process.in, process.out, process.err in a similar fashion.
     *
     * @param self a Process instance
     * @return the OutputStream for the process
     * @since 1.0
     */
    public static OutputStream getOut(Process self) {
        return self.getOutputStream();
    }

    /**
     * Overloads the left shift operator (&lt;&lt;) to provide an append mechanism
     * to pipe data to a Process.
     *
     * @param self  a Process instance
     * @param value a value to append
     * @return a Writer
     * @throws java.io.IOException if an IOException occurs.
     * @since 1.0
     */
    public static Writer leftShift(Process self, Object value) throws IOException {
        return DefaultGroovyMethods.leftShift(self.getOutputStream(), value);
    }

    /**
     * Overloads the left shift operator to provide an append mechanism
     * to pipe into a Process
     *
     * @param self  a Process instance
     * @param value data to append
     * @return an OutputStream
     * @throws java.io.IOException if an IOException occurs.
     * @since 1.0
     */
    public static OutputStream leftShift(Process self, byte[] value) throws IOException {
        return DefaultGroovyMethods.leftShift(self.getOutputStream(), value);
    }

    /**
     * Wait for the process to finish during a certain amount of time, otherwise stops the process.
     *
     * @param self           a Process
     * @param numberOfMillis the number of milliseconds to wait before stopping the process
     * @since 1.0
     */
    public static void waitForOrKill(Process self, long numberOfMillis) {
        ProcessRunner runnable = new ProcessRunner(self);
        Thread thread = new Thread(runnable);
        thread.start();
        runnable.waitForOrKill(numberOfMillis);
    }

    /**
     * Gets the output and error streams from a process and reads them
     * to keep the process from blocking due to a full output buffer.
     * The stream data is thrown away but blocking due to a full output buffer is avoided.
     * Use this method if you don't care about the standard or error output and just
     * want the process to run silently - use carefully however, because since the stream
     * data is thrown away, it might be difficult to track down when something goes wrong.
     * For this, two Threads are started, so this method will return immediately.
     *
     * @param self a Process
     * @since 1.0
     */
    public static void consumeProcessOutput(Process self) {
        consumeProcessOutput(self, (OutputStream)null, (OutputStream)null);
    }

    /**
     * Gets the output and error streams from a process and reads them
     * to keep the process from blocking due to a full output buffer.
     * The processed stream data is appended to the supplied Appendable.
     * For this, two Threads are started, so this method will return immediately.
     *
     * @param self a Process
     * @param output an Appendable to capture the process stdout
     * @param error an Appendable to capture the process stderr
     * @since 1.7.5
     */
    public static void consumeProcessOutput(Process self, Appendable output, Appendable error) {
        consumeProcessOutputStream(self, output);
        consumeProcessErrorStream(self, error);
    }

    /**
     * Gets the output and error streams from a process and reads them
     * to keep the process from blocking due to a full output buffer.
     * The processed stream data is appended to the supplied OutputStream.
     * For this, two Threads are started, so this method will return immediately.
     *
     * @param self a Process
     * @param output an OutputStream to capture the process stdout
     * @param error an OutputStream to capture the process stderr
     * @since 1.5.2
     */
    public static void consumeProcessOutput(Process self, OutputStream output, OutputStream error) {
        consumeProcessOutputStream(self, output);
        consumeProcessErrorStream(self, error);
    }

    /**
     * Gets the output and error streams from a process and reads them
     * to keep the process from blocking due to a full output buffer.
     * The stream data is thrown away but blocking due to a full output buffer is avoided.
     * Use this method if you don't care about the standard or error output and just
     * want the process to run silently - use carefully however, because since the stream
     * data is thrown away, it might be difficult to track down when something goes wrong.
     * For this, two Threads are started, but join()ed, so we wait.
     * As implied by the waitFor... name, we also wait until we finish
     * as well.
     *
     * @param self a Process
     * @since 1.6.5
     */
    public static void waitForProcessOutput(Process self) {
        waitForProcessOutput(self, (OutputStream)null, (OutputStream)null);
    }

    /**
     * Gets the output and error streams from a process and reads them
     * to keep the process from blocking due to a full output buffer.
     * The processed stream data is appended to the supplied Appendable.
     * For this, two Threads are started, but join()ed, so we wait.
     * As implied by the waitFor... name, we also wait until we finish
     * as well.
     *
     * @param self a Process
     * @param output an Appendable to capture the process stdout
     * @param error an Appendable to capture the process stderr
     * @since 1.7.5
     */
    public static void waitForProcessOutput(Process self, Appendable output, Appendable error) {
        Thread tout = consumeProcessOutputStream(self, output);
        Thread terr = consumeProcessErrorStream(self, error);
        try { tout.join(); } catch (InterruptedException ignore) {}
        try { terr.join(); } catch (InterruptedException ignore) {}
        try { self.waitFor(); } catch (InterruptedException ignore) {}
    }

    /**
     * Gets the output and error streams from a process and reads them
     * to keep the process from blocking due to a full output buffer.
     * The processed stream data is appended to the supplied OutputStream.
     * For this, two Threads are started, but join()ed, so we wait.
     * As implied by the waitFor... name, we also wait until we finish
     * as well.
     *
     * @param self a Process
     * @param output an OutputStream to capture the process stdout
     * @param error an OutputStream to capture the process stderr
     * @since 1.6.5
     */
    public static void waitForProcessOutput(Process self, OutputStream output, OutputStream error) {
        Thread tout = consumeProcessOutputStream(self, output);
        Thread terr = consumeProcessErrorStream(self, error);
        try { tout.join(); } catch (InterruptedException ignore) {}
        try { terr.join(); } catch (InterruptedException ignore) {}
        try { self.waitFor(); } catch (InterruptedException ignore) {}
    }

    /**
     * Gets the error stream from a process and reads it
     * to keep the process from blocking due to a full buffer.
     * The processed stream data is appended to the supplied OutputStream.
     * A new Thread is started, so this method will return immediately.
     *
     * @param self a Process
     * @param err an OutputStream to capture the process stderr
     * @return the Thread
     * @since 1.5.2
     */
    public static Thread consumeProcessErrorStream(Process self, OutputStream err) {
        Thread thread = new Thread(new ByteDumper(self.getErrorStream(), err));
        thread.start();
        return thread;
    }

    /**
     * Gets the error stream from a process and reads it
     * to keep the process from blocking due to a full buffer.
     * The processed stream data is appended to the supplied Appendable.
     * A new Thread is started, so this method will return immediately.
     *
     * @param self a Process
     * @param error an Appendable to capture the process stderr
     * @return the Thread
     * @since 1.7.5
     */
    public static Thread consumeProcessErrorStream(Process self, Appendable error) {
        Thread thread = new Thread(new TextDumper(self.getErrorStream(), error));
        thread.start();
        return thread;
    }

    /**
     * Gets the output stream from a process and reads it
     * to keep the process from blocking due to a full output buffer.
     * The processed stream data is appended to the supplied Appendable.
     * A new Thread is started, so this method will return immediately.
     *
     * @param self a Process
     * @param output an Appendable to capture the process stdout
     * @return the Thread
     * @since 1.7.5
     */
    public static Thread consumeProcessOutputStream(Process self, Appendable output) {
        Thread thread = new Thread(new TextDumper(self.getInputStream(), output));
        thread.start();
        return thread;
    }

    /**
     * Gets the output stream from a process and reads it
     * to keep the process from blocking due to a full output buffer.
     * The processed stream data is appended to the supplied OutputStream.
     * A new Thread is started, so this method will return immediately.
     *
     * @param self a Process
     * @param output an OutputStream to capture the process stdout
     * @return the Thread
     * @since 1.5.2
     */
    public static Thread consumeProcessOutputStream(Process self, OutputStream output) {
        Thread thread = new Thread(new ByteDumper(self.getInputStream(), output));
        thread.start();
        return thread;
    }

    /**
     * Creates a new BufferedWriter as stdin for this process,
     * passes it to the closure, and ensures the stream is flushed
     * and closed after the closure returns.
     * A new Thread is started, so this method will return immediately.
     *
     * @param self a Process
     * @param closure a closure
     * @since 1.5.2
     */
    public static void withWriter(final Process self, final Closure closure) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    DefaultGroovyMethods.withWriter(new BufferedOutputStream(getOut(self)), closure);
                } catch (IOException e) {
                    throw new GroovyRuntimeException("exception while reading process stream", e);
                }
            }
        }).start();
    }

    /**
     * Creates a new buffered OutputStream as stdin for this process,
     * passes it to the closure, and ensures the stream is flushed
     * and closed after the closure returns.
     * A new Thread is started, so this method will return immediately.
     *
     * @param self a Process
     * @param closure a closure
     * @since 1.5.2
     */
    public static void withOutputStream(final Process self, final Closure closure) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    DefaultGroovyMethods.withStream(new BufferedOutputStream(getOut(self)), closure);
                } catch (IOException e) {
                    throw new GroovyRuntimeException("exception while reading process stream", e);
                }
            }
        }).start();
    }

    /**
     * Allows one Process to asynchronously pipe data to another Process.
     *
     * @param left  a Process instance
     * @param right a Process to pipe output to
     * @return the second Process to allow chaining
     * @throws java.io.IOException if an IOException occurs.
     * @since 1.5.2
     */
    public static Process pipeTo(final Process left, final Process right) throws IOException {
        new Thread(new Runnable() {
            public void run() {
                InputStream in = new BufferedInputStream(getIn(left));
                OutputStream out = new BufferedOutputStream(getOut(right));
                byte[] buf = new byte[8192];
                int next;
                try {
                    while ((next = in.read(buf)) != -1) {
                        out.write(buf, 0, next);
                    }
                } catch (IOException e) {
                    throw new GroovyRuntimeException("exception while reading process stream", e);
                } finally {
                    closeWithWarning(out);
                }
            }
        }).start();
        return right;
    }

    /**
     * Overrides the or operator to allow one Process to asynchronously
     * pipe data to another Process.
     *
     * @param left  a Process instance
     * @param right a Process to pipe output to
     * @return the second Process to allow chaining
     * @throws java.io.IOException if an IOException occurs.
     * @since 1.5.1
     */
    public static Process or(final Process left, final Process right) throws IOException {
        return pipeTo(left, right);
    }

    /**
     * A Runnable which waits for a process to complete together with a notification scheme
     * allowing another thread to wait a maximum number of seconds for the process to complete
     * before killing it.
     *
     * @since 1.0
     */
    protected static class ProcessRunner implements Runnable {
        Process process;
        private boolean finished;

        public ProcessRunner(Process process) {
            this.process = process;
        }

        private void doProcessWait() {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        public void run() {
            doProcessWait();
            synchronized (this) {
                notifyAll();
                finished = true;
            }
        }

        public synchronized void waitForOrKill(long millis) {
            if (!finished) {
                try {
                    wait(millis);
                } catch (InterruptedException e) {
                    // Ignore
                }
                if (!finished) {
                    process.destroy();
                    doProcessWait();
                }
            }
        }
    }

    private static class TextDumper implements Runnable {
        InputStream in;
        Appendable app;

        public TextDumper(InputStream in, Appendable app) {
            this.in = in;
            this.app = app;
        }

        public void run() {
            InputStreamReader isr = new InputStreamReader(in);
            BufferedReader br = new BufferedReader(isr);
            String next;
            try {
                while ((next = br.readLine()) != null) {
                    if (app != null) {
                        app.append(next);
                        app.append("\n");
                    }
                }
            } catch (IOException e) {
                throw new GroovyRuntimeException("exception while reading process stream", e);
            }
        }
    }

    private static class ByteDumper implements Runnable {
        InputStream in;
        OutputStream out;

        public ByteDumper(InputStream in, OutputStream out) {
            this.in = new BufferedInputStream(in);
            this.out = out;
        }

        public void run() {
            byte[] buf = new byte[8192];
            int next;
            try {
                while ((next = in.read(buf)) != -1) {
                    if (out != null) out.write(buf, 0, next);
                }
            } catch (IOException e) {
                throw new GroovyRuntimeException("exception while dumping process stream", e);
            }
        }
    }

}
