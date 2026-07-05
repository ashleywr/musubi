package com.sanhiruzu.musubi.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.*;

public class HankModelLoader {
    private static final Map<ResourceLocation, HankModel> MODEL_CACHE = new HashMap<>();

    public static class Face {
        public float[] positions = new float[12]; // 4 vertices * 3 coords
        public float[] uvs = new float[8];       // 4 vertices * 2 uv
        public float[] normal = new float[3];    // normal vector
    }

    public static class HankModel {
        public List<Face> faces = new ArrayList<>();
        public String textureKey;
    }

    public static HankModel loadModel(ResourceLocation modelPath) {
        if (MODEL_CACHE.containsKey(modelPath)) {
            return MODEL_CACHE.get(modelPath);
        }

        try {
            HankModel model = new HankModel();
            ResourceLocation modelLocation = ResourceLocation.fromNamespaceAndPath(
                modelPath.getNamespace(),
                "models/" + modelPath.getPath() + ".json"
            );

            Resource resource = Minecraft.getInstance().getResourceManager()
                .getResourceOrThrow(modelLocation);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open()))) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                // Extract textures map
                Map<String, String> textures = new HashMap<>();
                if (json.has("textures")) {
                    for (var entry : json.getAsJsonObject("textures").entrySet()) {
                        textures.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }

                // Parse elements
                if (json.has("elements")) {
                    JsonArray elements = json.getAsJsonArray("elements");
                    for (JsonElement elemElem : elements) {
                        JsonObject elem = elemElem.getAsJsonObject();
                        parseElement(elem, textures, model);
                    }
                }
            }

            MODEL_CACHE.put(modelPath, model);
            return model;

        } catch (IOException e) {
            System.err.println("Failed to load hank model: " + modelPath);
            e.printStackTrace();
            return new HankModel(); // Return empty model on failure
        }
    }

    private static void parseElement(JsonObject elem, Map<String, String> textures, HankModel model) {
        float[] from = parseFloatArray(elem.getAsJsonArray("from"));
        float[] to = parseFloatArray(elem.getAsJsonArray("to"));

        // Convert from 0-16 block units to 0-1 normalized units
        float x0 = from[0] / 16f, y0 = from[1] / 16f, z0 = from[2] / 16f;
        float x1 = to[0] / 16f,   y1 = to[1] / 16f,   z1 = to[2] / 16f;

        if (elem.has("faces")) {
            JsonObject faces = elem.getAsJsonObject("faces");

            // North (-Z)
            if (faces.has("north")) {
                addFace(model, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0,
                        faces.getAsJsonObject("north"), textures, 0, 0, -1);
            }

            // South (+Z)
            if (faces.has("south")) {
                addFace(model, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1,
                        faces.getAsJsonObject("south"), textures, 0, 0, 1);
            }

            // West (-X)
            if (faces.has("west")) {
                addFace(model, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1,
                        faces.getAsJsonObject("west"), textures, -1, 0, 0);
            }

            // East (+X)
            if (faces.has("east")) {
                addFace(model, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0,
                        faces.getAsJsonObject("east"), textures, 1, 0, 0);
            }

            // Up (+Y)
            if (faces.has("up")) {
                addFace(model, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
                        faces.getAsJsonObject("up"), textures, 0, 1, 0);
            }

            // Down (-Y)
            if (faces.has("down")) {
                addFace(model, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
                        faces.getAsJsonObject("down"), textures, 0, -1, 0);
            }
        }
    }

    private static void addFace(HankModel model,
                               float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float x3, float y3, float z3,
                               JsonObject faceObj, Map<String, String> textures,
                               float nx, float ny, float nz) {
        Face face = new Face();
        face.positions[0] = x0; face.positions[1] = y0; face.positions[2] = z0;
        face.positions[3] = x1; face.positions[4] = y1; face.positions[5] = z1;
        face.positions[6] = x2; face.positions[7] = y2; face.positions[8] = z2;
        face.positions[9] = x3; face.positions[10] = y3; face.positions[11] = z3;

        face.normal[0] = nx;
        face.normal[1] = ny;
        face.normal[2] = nz;

        // Parse UV
        if (faceObj.has("uv")) {
            float[] uv = parseFloatArray(faceObj.getAsJsonArray("uv"));
            face.uvs[0] = uv[0] / 16f; face.uvs[1] = uv[1] / 16f;
            face.uvs[2] = uv[2] / 16f; face.uvs[3] = uv[1] / 16f;
            face.uvs[4] = uv[2] / 16f; face.uvs[5] = uv[3] / 16f;
            face.uvs[6] = uv[0] / 16f; face.uvs[7] = uv[3] / 16f;
        }

        model.faces.add(face);
    }

    private static float[] parseFloatArray(JsonArray arr) {
        float[] result = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.get(i).getAsFloat();
        }
        return result;
    }
}
