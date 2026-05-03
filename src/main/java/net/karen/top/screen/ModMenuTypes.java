package net.karen.top.screen;

import net.karen.top.Top;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Top.MOD_ID);

    // AnvilMenu extends ItemCombiner
    public static final DeferredHolder<MenuType<?>, MenuType<TopAnvilBlockMenu>> TOP_MENU =
            registerSimple(Identifier.fromNamespaceAndPath(Top.MOD_ID, "top_block"), TopAnvilBlockMenu::new);


    private static <T extends AbstractContainerMenu>DeferredHolder<MenuType<?>, MenuType<T>> registerMenuType(String name,
                                                                                                              IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }


    public static <T extends AbstractContainerMenu>DeferredHolder<MenuType<?>, MenuType<T>>  registerSimple(Identifier id, MenuType.MenuSupplier<T> factory) {
        MenuType<T> type = new MenuType<>(factory, FeatureFlags.VANILLA_SET);
        return MENUS.register(id.getPath(), () -> type);
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

}
