package net.karen.top.datagen;

import net.karen.top.Top;
import net.karen.top.block.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, Top.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        addBlock(ModBlocks.TOP_ANVIL, "Top Anvil Block");
        add("creativetab.top_blocks",  "Top Blocks");

        add("top.configuration.title", "Top Configs");
        add("top.configuration.section.top.common.toml", "Top Configs");

        add("top.configuration.section.top.common.toml.title", "Top Configs");

        add("top.configuration.items", "Item List");
        add("top.configuration.logDirtBlock", "Log Dirt Block");
        add("top.configuration.magicNumberIntroduction", "Magic Number Text");
        add("top.configuration.magicNumber", "Magic Number");
    }
}