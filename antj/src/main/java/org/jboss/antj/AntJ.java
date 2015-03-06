/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.antj;

import org.jboss.antj.antrunner.spi.AntRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class AntJ {
    private static final ResourceBundle RESOURCE_BUNDLE;
    private static Logger LOG = Logger.getLogger(AntJ.class.getName(), AntJ.class.getPackage().getName() + ".resource");

    static {
        try {
            RESOURCE_BUNDLE = ResourceBundle.getBundle(AntJ.class.getPackage().getName() + ".resource");
        } catch (MissingResourceException e) {
            throw new Error("Fatal: Resource for antj is missing", e);
        }
    }

    private static void ant(final File directory, final File antFile, final String... args) throws IOException {
        try {
            final Class<AntRunner> cls = (Class<AntRunner>) AntJ.class.getClassLoader().loadClass("org.jboss.antj.antrunner.AntRunnerImpl");
            try {
                final AntRunner runner = cls.newInstance();
                runner.ant(directory, antFile, args);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        } catch (ClassNotFoundException ex) {
            final List<URL> urls = new ArrayList<URL>();
            Files.walkFileTree(Paths.get(directory.getAbsolutePath() + File.separator + "lib"), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path path, final BasicFileAttributes basicFileAttributes) throws IOException {
                    urls.add(path.toUri().toURL());
                    return FileVisitResult.CONTINUE;
                }
            });
            final URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[0]), AntJ.class.getClassLoader());
            try {
                final Class<AntRunner> cls = (Class<AntRunner>) classLoader.loadClass("org.jboss.antj.antrunner.AntRunnerImpl");
                try {
                    final AntRunner runner = cls.newInstance();
                    runner.ant(directory, antFile, args);
                } catch (InstantiationException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void ant(final File directory, final String antFile, final String... args) throws IOException {
        ant(directory, new File(directory, antFile), args);
    }

    static void antj(final ZipFile zipFile, final String... args) throws IOException {
        final Path tempPath = Files.createTempDirectory("antj", new FileAttribute[0]);
        tempPath.toFile().deleteOnExit();
        LOG.finest("Using temporary path " + tempPath);

        extract(tempPath.toFile(), zipFile);

        ant(tempPath.toFile(), ".antj/build.xml", args);
    }

    private static byte[] copyBuf = new byte[8192];

    private static void copy(final InputStream from, final File to) throws IOException {
        final OutputStream out = new FileOutputStream(to);
        try {
            copy(from, out);
        } finally {
            out.close();
        }
    }

    private static void copy(final InputStream from, final OutputStream to) throws IOException {
        int n;
        while ((n = from.read(copyBuf)) != -1)
            to.write(copyBuf, 0, n);
    }

    private static void extract(final File destination, final ZipFile zf) throws IOException {
        final Set<ZipEntry> dirs = new HashSet<ZipEntry>() {
            @Override
            public boolean add(final ZipEntry zipEntry) {
                if (zipEntry == null)
                    return false;
                return super.add(zipEntry);
            }
        };
        final Enumeration<? extends ZipEntry> zes = zf.entries();
        while (zes.hasMoreElements()) {
            ZipEntry e = zes.nextElement();
            dirs.add(extractFile(destination, zf.getInputStream(e), e));
        }
        updateLastModifiedTime(destination, dirs);
    }

    private static ZipEntry extractFile(final File destination, final InputStream is, final ZipEntry e) throws IOException {
        ZipEntry rc = null;
        String name = e.getName();
        File f = new File(destination, e.getName().replace('/', File.separatorChar));
        if (e.isDirectory()) {
            if (f.exists()) {
                if (!f.isDirectory()) {
                    throw new IOException(formatMsg("error.create.dir", f.getPath()));
                }
            } else {
                if (!f.mkdirs()) {
                    throw new IOException(formatMsg("error.create.dir", f.getPath()));
                } else {
                    rc = e;
                }
                File toDelete = f;
                do {
                    toDelete.deleteOnExit();
                    toDelete = toDelete.getParentFile();
                } while (!toDelete.equals(destination));
            }

            LOG.log(Level.FINE, "out.create", name);
        } else {
            if (f.getParent() != null) {
                File d = new File(f.getParent());
                if (!d.exists() && !d.mkdirs() || !d.isDirectory()) {
                    throw new IOException(formatMsg(
                            "error.create.dir", d.getPath()));
                }
            }
            try {
                copy(is, f);
                f.deleteOnExit();
            } finally {
                if (is instanceof ZipInputStream)
                    ((ZipInputStream)is).closeEntry();
                else
                    is.close();
            }
            if (e.getMethod() == ZipEntry.DEFLATED) {
                LOG.log(Level.FINE, "out.inflated", name);
            } else {
                LOG.log(Level.FINE, "out.extracted", name);
            }
        }
        long lastModified = e.getTime();
        if (lastModified != -1) {
            f.setLastModified(lastModified);
        }
        return rc;
    }

    private static String formatMsg(final String key, final String arg) {
        final String msg = getMsg(key);
        return MessageFormat.format(msg, arg);
    }

    private static String getMsg(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            throw new Error("Error in resource bundle", e);
        }
    }

    public static void main(String... args) throws IOException, InterruptedException {
        final ZipFile zipFile = new ZipFile(args[0]);
        try {
            antj(zipFile, Arrays.copyOfRange(args, 1, args.length));
        } finally {
            zipFile.close();
        }

        System.exit(0);
    }

    private static void updateLastModifiedTime(final File destination, final Set<ZipEntry> zes) throws IOException {
        for (final ZipEntry ze : zes) {
            final long lastModified = ze.getTime();
            if (lastModified != -1) {
                final File f = new File(destination, ze.getName().replace('/', File.separatorChar));
                f.setLastModified(lastModified);
            }
        }
    }
}
