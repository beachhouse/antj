/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2015, Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarFile;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class EmbeddedAntJ {
    public static void main(String... args) throws IOException, InterruptedException {
        final URL url = EmbeddedAntJ.class.getResource("/.antj/build.xml");
        final String protocol = url.getProtocol();
        if (protocol.equals("jar")) {
            final JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
            final JarFile jarFile = jarURLConnection.getJarFile();
            try {
                AntJ.antj(jarFile);
            } finally {
                jarFile.close();
            }
        } else if (protocol.equals("file")) {
            throw new RuntimeException("NYI");
        } else {
            throw new RuntimeException("Protocol " + protocol + " is not supported");
        }
        System.exit(0);
    }
}
