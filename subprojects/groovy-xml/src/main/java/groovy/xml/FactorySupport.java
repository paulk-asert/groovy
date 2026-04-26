/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package groovy.xml;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.security.PrivilegedExceptionAction;

/**
 * Support class for creating XML Factories
 */
public class FactorySupport {
    /**
     * Runs the supplied factory creation action and normalizes checked failures.
     *
     * @param action the action creating the factory instance
     * @return the created factory
     * @throws ParserConfigurationException if the factory cannot be configured
     */
    static Object createFactory(PrivilegedExceptionAction action) throws ParserConfigurationException {
        try {
            return action.run();
        } catch (ParserConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a new {@link DocumentBuilderFactory}.
     *
     * @return a newly created document builder factory
     * @throws ParserConfigurationException if the factory cannot be created
     */
    public static DocumentBuilderFactory createDocumentBuilderFactory() throws ParserConfigurationException {
        return (DocumentBuilderFactory) createFactory(DocumentBuilderFactory::newInstance);
    }

    /**
     * Creates a new {@link SAXParserFactory}.
     *
     * @return a newly created SAX parser factory
     * @throws ParserConfigurationException if the factory cannot be created
     */
    public static SAXParserFactory createSaxParserFactory() throws ParserConfigurationException {
        return (SAXParserFactory) createFactory(SAXParserFactory::newInstance);
    }
}
