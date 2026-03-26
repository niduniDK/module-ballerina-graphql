/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.graphql.compiler.endpointyaml.generator;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.ServiceDeclarationSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.*;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class FileNameGeneratorUtil {

    private static final String SLASH = "/";
    private static final String UNDERSCORE = "_";
    private static final String HYPHEN = "-";
    private final Map<Integer, String> services = new HashMap<>();

    private static final String YAML_EXTENSION = "_yaml";
    private static final String JSON_EXTENSION = "_json";

    private final SyntaxNodeAnalysisContext context;
    private String schemaExtension;

    public FileNameGeneratorUtil(SyntaxNodeAnalysisContext context, String schemaExtension) {
        this.context = context;
        this.schemaExtension = schemaExtension;
    }

    public String getFileName() {
        SyntaxTree syntaxTree = context.syntaxTree();
        SemanticModel semanticModel = context.semanticModel();
        extractServiceNodes(syntaxTree.rootNode(), semanticModel);
        if (!(context.node() instanceof ServiceDeclarationNode node)) {
            String balFileName = syntaxTree.filePath().replaceAll(SLASH, UNDERSCORE).split("\\.")[0];
            return balFileName + schemaExtension;
        }

        Optional<Symbol> serviceSymbol = semanticModel.symbol(node);
        if (serviceSymbol.isEmpty()) {
            String basePathName = getServiceBasePath(node);
            if (!basePathName.isBlank()) {
                return getNormalizedFileName(basePathName) + schemaExtension;
            }
            String balFileName = syntaxTree.filePath().replaceAll(SLASH, UNDERSCORE).split("\\.")[0];
            return balFileName + schemaExtension;
        }

        return constructFileName(syntaxTree, services, serviceSymbol.get());
    }

    private void extractServiceNodes(ModulePartNode modulePartNode,
                                            SemanticModel semanticModel) {
        List<String> allServices = new ArrayList<>();
        for (Node node : modulePartNode.members()) {
            SyntaxKind syntaxKind = node.kind();
            if (syntaxKind.equals(SyntaxKind.SERVICE_DECLARATION)) {
                ServiceDeclarationNode serviceNode = (ServiceDeclarationNode) node;
                Optional<Symbol> serviceSymbol = semanticModel.symbol(serviceNode);
                if (serviceSymbol.isPresent() && serviceSymbol.get() instanceof ServiceDeclarationSymbol) {
                    StringBuilder basePath = new StringBuilder();
                    NodeList<Node> resourcePathNode = ((ServiceDeclarationNode) node).absoluteResourcePath();

                    for (Node identifierNode : resourcePathNode) {
                        basePath.append(identifierNode.toString().replace("\"", "").trim());
                    }

                    String service = basePath.toString();
                    String updateServiceName = service;
                    if (allServices.contains(service)) {
                        updateServiceName = service + HYPHEN + serviceSymbol.get().hashCode();
                    } else {
                        allServices.add(service);
                    }
                    this.services.put(serviceSymbol.get().hashCode(), updateServiceName);
                }
            }
        }
    }

    private String constructFileName(SyntaxTree syntaxTree, Map<Integer, String> services,
                                     Symbol serviceSymbol) {
        String serviceName = services.get(serviceSymbol.hashCode());
        String fileName = serviceName == null ? "" : getNormalizedFileName(serviceName);
        String balFileName = syntaxTree.filePath().replaceAll(SLASH, UNDERSCORE).split("\\.")[0];
        if (fileName.equals(SLASH)) {
            return balFileName + schemaExtension;
        } else if (fileName.contains(HYPHEN) && fileName.split(HYPHEN)[0].equals(SLASH) || fileName.isBlank()) {
            return balFileName + UNDERSCORE + serviceSymbol.hashCode() + schemaExtension;
        }
        return fileName + schemaExtension;
    }

    private String getServiceBasePath(ServiceDeclarationNode serviceNode) {
        StringBuilder basePath = new StringBuilder();
        NodeList<Node> resourcePathNode = serviceNode.absoluteResourcePath();
        for (Node identifierNode : resourcePathNode) {
            basePath.append(identifierNode.toString().replace("\"", "").trim());
        }
        return basePath.toString();
    }

    public static String getNormalizedFileName(String fileName) {
        String[] splitNames = fileName.split("[^a-zA-Z0-9]");
        if (splitNames.length > 0) {
            return Arrays.stream(splitNames)
                    .filter(namePart -> !namePart.isBlank())
                    .collect(Collectors.joining(UNDERSCORE));
        }
        return fileName;
    }


    /**
     * This method use for checking the duplicate files.
     *
     * @param outPath     output path for file generated
     * @param fileName given file name
     * @return file name with duplicate number tag
     */
    public static String resolveContractFileName(Path outPath, String fileName, Boolean isJson) {
        if (outPath != null && Files.exists(outPath)) {
            final File[] listFiles = new File(String.valueOf(outPath)).listFiles();
            if (listFiles != null) {
                fileName = checkAvailabilityOfGivenName(fileName, listFiles, isJson);
            }
        }
        return fileName;
    }

    private static String checkAvailabilityOfGivenName(String fileName, File[] listFiles, Boolean isJson) {
        for (File file : listFiles) {
            if (System.console() != null && file.getName().equals(fileName)) {
                String userInput = System.console().readLine("There is already a file named '" + file.getName() +
                        "' in the target location. Do you want to overwrite the file? [y/N] ");
                if (!Objects.equals(userInput.toLowerCase(Locale.ENGLISH), "y")) {
                    fileName = setGeneratedFileName(listFiles, fileName, isJson);
                }
            }
        }
        return fileName;
    }

    /**
     * This method for setting the file name for generated file.
     *
     * @param listFiles      generated files
     * @param fileName       File name
     */
    private static String setGeneratedFileName(File[] listFiles, String fileName, boolean isJson) {
        int duplicateCount = 0;
        for (File listFile : listFiles) {
            String listFileName = listFile.getName();
            if (listFileName.contains(".") && ((listFileName.split("\\.")).length >= 2)
                    && (listFileName.split("\\.")[0]
                    .equals(fileName.split("\\.")[0]))) {
                duplicateCount++;
            }
        }
        if (isJson) {
            return fileName.split("\\.")[0] + "." + duplicateCount + JSON_EXTENSION;
        }
        return fileName.split("\\.")[0] + "." + duplicateCount + YAML_EXTENSION;
    }

    /**
     * Writes a file with content to specified {@code filePath}.
     *
     * @param filePath valid file path to write the content
     * @param content  content of the file
     * @throws IOException when a file operation fails
     */
    public static void writeFile(Path filePath, String content) throws IOException {
        try (FileWriter writer = new FileWriter(filePath.toString(), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    /**
     * Copy content of a file/directory into another location.
     *
     * @param inputStream stream from which the data is read
     * @param outStream stream to which the data is written
     * @throws IOException if there is any error while reading from a file or writing to a file
     */
    public static <T extends InputStream, E extends OutputStream> void copyContent(T inputStream, E outStream)
            throws IOException {
        byte[] data = new byte[1024];
        int bytesRead = inputStream.read(data);
        while (bytesRead != -1) {
            outStream.write(data, 0, bytesRead);
            bytesRead = inputStream.read(data);
        }
    }
}
