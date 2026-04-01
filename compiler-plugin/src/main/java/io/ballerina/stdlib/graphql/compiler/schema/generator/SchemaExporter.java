package io.ballerina.stdlib.graphql.compiler.schema.generator;

import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.stdlib.graphql.commons.types.Schema;
import io.ballerina.stdlib.graphql.commons.utils.SdlSchemaStringGenerator;
import io.ballerina.stdlib.graphql.compiler.endpointyaml.generator.FileNameGeneratorUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.ballerina.stdlib.graphql.compiler.endpointyaml.generator.FileNameGeneratorUtil.resolveContractFileName;

public class SchemaExporter {
    private final Schema schema;
    private static final String ARTIFACT = "artifact";
    private static final String SDL_EXTENSION = ".graphql";

    private final SyntaxNodeAnalysisContext context;
    private final FileNameGeneratorUtil fileNameGeneratorUtil;

    private String schemaFileName = "";

    public SchemaExporter(Schema schema, SyntaxNodeAnalysisContext context) {
        this.schema = schema;
        this.context = context;

        this.fileNameGeneratorUtil = new FileNameGeneratorUtil(this.context, ".graphql");
    }

    public Schema getSchema() {
        return this.schema;
    }

    public void exportSchema() {
        Package currentPackage = this.context.currentPackage();
        Project project = currentPackage.project();
        Path outPath = project.targetDir();

        this.schemaFileName = this.fileNameGeneratorUtil.getFileName();
        if (this.schemaFileName.endsWith(SDL_EXTENSION)) {
            this.schemaFileName = this.schemaFileName.substring(0,
                    this.schemaFileName.length() - SDL_EXTENSION.length());
        }

        try {
            Files.createDirectories(Paths.get(outPath + "/" + ARTIFACT));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String fileName = resolveContractFileName(outPath.resolve(ARTIFACT),
                this.schemaFileName);

        Path path = outPath.resolve(ARTIFACT + "/" + fileName + SDL_EXTENSION);

        // Convert the custom Schema object to GraphQLSchema
        String sdlString = SdlSchemaStringGenerator.generate(this.schema);

        try {
            Files.writeString(path, sdlString);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
