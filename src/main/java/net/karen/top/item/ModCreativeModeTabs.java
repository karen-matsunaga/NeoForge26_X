package net.karen.top.item;

import net.karen.top.Top;
import net.karen.top.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Top.MOD_ID);


    public static final Supplier<CreativeModeTab> TOP_BLOCKS_TAB = CREATIVE_MODE_TABS.register("top_blocks_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.TOP_ANVIL))
                    .title(Component.translatable("creativetab.top_blocks"))
                    .displayItems((_, output) -> {
                        output.accept(ModBlocks.TOP_ANVIL);


                    }).build());



    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}