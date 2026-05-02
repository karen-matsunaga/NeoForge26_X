package net.karen.top.screen;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.karen.top.block.ModBlocks;
import net.karen.top.screen.menu.ModMenuTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class TopAnvilBlockMenu extends ItemCombinerMenu {
    public int repairItemCountCost;
    private @Nullable String itemName;
    private final DataSlot cost;
    private boolean onlyRenaming;

    public TopAnvilBlockMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public TopAnvilBlockMenu(int containerId, Inventory inventory, ContainerLevelAccess context) {
        super(ModMenuTypes.TOP_MENU.get(), containerId, inventory, context, createInputSlotDefinitions());
        this.cost = DataSlot.standalone();
        this.onlyRenaming = false;
        this.addDataSlot(this.cost);
    }

    private static ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create()
                                             .withSlot(0, 27, 47,
                                                       (_) -> true).withSlot(1, 76, 47,
                                                       (_) -> true).withResultSlot(2, 134, 47)
                                             .build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.TOP_ANVIL);
    }

    @Override
    public void createResult() {
        this.createResultInternal();
        ItemStack leftInput = this.inputSlots.getItem(0);
        ItemStack rightInput = this.inputSlots.getItem(1);

        if (!leftInput.isEmpty()) {
            var event = new AnvilUpdateEvent(leftInput, rightInput, this.itemName, resultSlots.getItem(0),
                                             this.getCost(), this.repairItemCountCost, player);
            // If the event is canceled, the anvil operation is void. Set the result to empty and the cost to zero.
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                resultSlots.setItem(0, ItemStack.EMPTY);
                this.setCost(0);
                this.repairItemCountCost = 0;
                return;
            }

            // Otherwise, update the results to the new values.
            resultSlots.setItem(0, event.getOutput());
            this.setCost(event.getXpCost());
            this.repairItemCountCost = event.getMaterialCost();
        }
    }

    protected boolean mayPickup(Player player, boolean hasItem) {
        return (player.hasInfiniteMaterials() || player.experienceLevel >= this.cost.get()) && this.cost.get() > 0;
    }

    protected void onTake(Player player, @NonNull ItemStack carried) {
        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-this.cost.get());
        }

        if (this.repairItemCountCost > 0) {
            ItemStack addition = this.inputSlots.getItem(1);
            if (!addition.isEmpty() && addition.getCount() > this.repairItemCountCost) {
                addition.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, addition);
            }
            else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        }
        else if (!this.onlyRenaming) {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(0);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!StringUtil.isBlank(this.itemName) && !this.inputSlots.getItem(0).getHoverName().getString().equals(this.itemName)) {
                serverPlayer.getTextFilter().processStreamMessage(this.itemName);
            }
        }

        this.inputSlots.setItem(0, ItemStack.EMPTY);
        this.access.execute((level, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!player.hasInfiniteMaterials() && state.is(BlockTags.ANVIL) && player.getRandom().nextFloat() < 0.12F) {
                BlockState newBlockState = AnvilBlock.damage(state);
                if (newBlockState == null) {
                    level.removeBlock(pos, false);
                    level.levelEvent(1029, pos, 0);
                }
                else {
                    level.setBlock(pos, newBlockState, 2);
                    level.levelEvent(1030, pos, 0);
                }
            }
            else {
                level.levelEvent(1030, pos, 0);
            }

        });
    }

    protected void createResultInternal() {
        ItemStack input = this.inputSlots.getItem(0);
        this.onlyRenaming = false;
        this.cost.set(1);
        int price = 0;
        long tax = 0L;
        int namingCost = 0;
        if (!input.isEmpty() && EnchantmentHelper.canStoreEnchantments(input)) {
            ItemStack result = input.copy();
            ItemStack addition = this.inputSlots.getItem(1);
            ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result));
            tax += (long) input.getOrDefault(DataComponents.REPAIR_COST, 0) + (long) addition.getOrDefault(DataComponents.REPAIR_COST, 0);
            this.repairItemCountCost = 0;
            if (!addition.isEmpty()) {
                boolean usingBook = addition.has(DataComponents.STORED_ENCHANTMENTS);
                if (result.isDamageableItem() && input.isValidRepairItem(addition)) {
                    int repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                    if (repairAmount <= 0) {
                        this.resultSlots.setItem(0, ItemStack.EMPTY);
                        this.cost.set(0);
                        return;
                    }

                    int count;
                    for(count = 0; repairAmount > 0 && count < addition.getCount(); ++count) {
                        int resultDamage = result.getDamageValue() - repairAmount;
                        result.setDamageValue(resultDamage);
                        ++price;
                        repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = count;
                }
                else {
                    // Enchanted item + Book
                    if (!usingBook && addition.is(Items.BOOK)) {
                        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                        ItemEnchantments oldEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
                        book.set(DataComponents.STORED_ENCHANTMENTS, oldEnchantments);
                        for (Object2IntMap.Entry<Holder<Enchantment>> entry : oldEnchantments.entrySet()) {
                            Holder<Enchantment> enchantmentHolder = entry.getKey();
                            int level =  entry.getIntValue();
                            book.enchant(enchantmentHolder, level);
                        }
                        int baseCost = addition.getOrDefault(DataComponents.REPAIR_COST, 0);
                        addition.set(DataComponents.REPAIR_COST, baseCost);
                        EnchantmentHelper.setEnchantments(book, oldEnchantments);
                        this.resultSlots.setItem(0, book); // book
                        this.cost.set(1); // Cost > 0
                        this.broadcastChanges();
                        return;
                    }

                    if (!usingBook && (!result.is(addition.getItem()) || !result.isDamageableItem())) {
                        this.resultSlots.setItem(0, ItemStack.EMPTY);
                        this.cost.set(0);
                        return;
                    }

                    if (result.isDamageableItem() && !usingBook) {
                        int resultDamage = getResultDamage(input, addition, result);

                        if (resultDamage < result.getDamageValue()) {
                            result.setDamageValue(resultDamage);
                            price += 2;
                        }
                    }

                    ItemEnchantments additionalEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(addition);
                    boolean isAnyEnchantmentCompatible = false;
                    boolean isAnyEnchantmentNotCompatible = false;

                    for (Object2IntMap.Entry<Holder<Enchantment>> entry : additionalEnchantments.entrySet()) {
                        Holder<Enchantment> enchantmentHolder = entry.getKey();
                        int current = enchantments.getLevel(enchantmentHolder);
                        int level = entry.getIntValue();
                        level = current + level; // Sum current and level enchantments
                        Enchantment enchantment = enchantmentHolder.value();
                        boolean compatible = input.supportsEnchantment(enchantmentHolder);
                        if (this.player.getAbilities().instabuild) {
                            compatible = true;
                        }

                        for (Holder<Enchantment> other : enchantments.keySet()) {
                            if (!other.equals(enchantmentHolder) && !Enchantment.areCompatible(enchantmentHolder, other)) {
                                compatible = false;
                                ++price;
                            }
                        }

                        if (!compatible) {
                            isAnyEnchantmentNotCompatible = true;
                        }
                        else {
                            isAnyEnchantmentCompatible = true;
                            if (level > enchantment.getMaxLevel()) {
                                level = Math.min(level, 255); // Max level enchantment 255
                            }

                            enchantments.set(enchantmentHolder, level);
                            int fee = enchantment.getAnvilCost();
                            if (usingBook) {
                                fee = Math.max(1, fee / 2);
                            }

                            price += fee * level;
                            if (input.getCount() > 1) {
                                price = 40;
                            }
                        }
                    }

                    if (isAnyEnchantmentNotCompatible && !isAnyEnchantmentCompatible) {
                        this.resultSlots.setItem(0, ItemStack.EMPTY);
                        this.cost.set(0);
                        return;
                    }
                }
            }

            if (this.itemName != null && !StringUtil.isBlank(this.itemName)) {
                if (!this.itemName.equals(input.getHoverName().getString())) {
                    namingCost = 1;
                    price += namingCost;
                    result.set(DataComponents.CUSTOM_NAME, Component.literal(this.itemName));
                }
            }
            else if (input.has(DataComponents.CUSTOM_NAME)) {
                namingCost = 1;
                price += namingCost;
                result.remove(DataComponents.CUSTOM_NAME);
            }

            int finalPrice = price <= 0 ? 0 : (int) Mth.clamp(tax + (long)price, 0L, 2147483647L);
            this.cost.set(finalPrice);
            if (price <= 0) {
                result = ItemStack.EMPTY;
            }

            if (namingCost == price && namingCost > 0) {
                if (this.cost.get() >= 40) {
                    this.cost.set(39);
                }

                this.onlyRenaming = true;
            }

            if (this.cost.get() >= 40 && !this.player.hasInfiniteMaterials()) {
                result = ItemStack.EMPTY;
            }

            if (!result.isEmpty()) {
                int baseCost = result.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (baseCost < addition.getOrDefault(DataComponents.REPAIR_COST, 0)) {
                    baseCost = addition.getOrDefault(DataComponents.REPAIR_COST, 0);
                }

                if (namingCost != price || namingCost == 0) {
                    baseCost = calculateIncreasedRepairCost(baseCost);
                }

                result.set(DataComponents.REPAIR_COST, baseCost);
                EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
            }

            this.resultSlots.setItem(0, result);
            this.broadcastChanges();
        }
        else {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
        }

    }

    private static int getResultDamage(ItemStack input, ItemStack addition, ItemStack result) {
        int remaining1 = input.getMaxDamage() - input.getDamageValue();
        int remaining2 = addition.getMaxDamage() - addition.getDamageValue();
        int additional = remaining2 + result.getMaxDamage() * 12 / 100;
        int remaining = remaining1 + additional;
        int resultDamage = result.getMaxDamage() - remaining;
        if (resultDamage < 0) {
            resultDamage = 0;
        }
        return resultDamage;
    }

    public static int calculateIncreasedRepairCost(int baseCost) {
        return (int)Math.min((long)baseCost * 2L + 1L, 2147483647L);
    }

    public boolean setItemName(String name) {
        String validatedName = validateName(name);
        if (validatedName != null && !validatedName.equals(this.itemName)) {
            this.itemName = validatedName;
            if (this.getSlot(2).hasItem()) {
                ItemStack itemStack = this.getSlot(2).getItem();
                if (StringUtil.isBlank(validatedName)) {
                    itemStack.remove(DataComponents.CUSTOM_NAME);
                }
                else {
                    itemStack.set(DataComponents.CUSTOM_NAME, Component.literal(validatedName));
                }
            }

            this.createResult();
            return true;
        }
        else {
            return false;
        }
    }

    private static @Nullable String validateName(String name) {
        String filteredName = StringUtil.filterText(name);
        return filteredName.length() <= 50 ? filteredName : null;
    }

    public int getCost() {
        return this.cost.get();
    }

    public void setCost(int value) {
        this.cost.set(Math.max(0, value));
    }
}