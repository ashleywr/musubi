package com.sanhiruzu.musubi;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class MusubiTags {

    public static final class Blocks {
        /** Blocks eligible to become a Shinboku when wrapped with Shimenawa. */
        public static final TagKey<Block> SHINBOKU_VALID =
            block("shinboku_valid");

        private Blocks() {}
    }

    private static TagKey<Block> block(String name) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, name));
    }

    private MusubiTags() {}
}
