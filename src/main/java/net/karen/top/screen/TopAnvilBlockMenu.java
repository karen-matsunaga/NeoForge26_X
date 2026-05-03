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

    private final int INPUT_SLOT_0 = 0;
    private final int INPUT_SLOT_1 = 1;
    private final int RESULT_SLOT = 0;

    public int getCost() {
        return this.cost.get();
    }

    public void setCost(int value) {
        this.cost.set(Math.max(0, value));
    }

    private ItemStack getInputItem(int slot) {
        return this.inputSlots.getItem(slot);
    }

    private void setInputItem(int slot, @NonNull ItemStack stack) {
        this.inputSlots.setItem(slot, stack);
    }

    private ItemStack getResultItem() {
        return resultSlots.getItem(RESULT_SLOT);
    }

    private void setResultItem(@NonNull ItemStack stack) {
        resultSlots.setItem(RESULT_SLOT, stack);
    }

    public TopAnvilBlockMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public TopAnvilBlockMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenuTypes.TOP_MENU.get(), containerId, inventory, access, createInputSlotDefinitions());
        this.cost = DataSlot.standalone();
        this.onlyRenaming = false;
        this.addDataSlot(this.cost);
    }

    private static ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create().withSlot(0, 27, 47, (_) -> true)
                                                      .withSlot(1, 76, 47, (_) -> true)
                                                      .withResultSlot(2, 134, 47).build();
    }

    @Override
    protected boolean isValidBlock(BlockState state) {
        return state.is(ModBlocks.TOP_ANVIL);
    }

    @Override
    public void createResult() {
        this.createResultInternal();
        ItemStack leftInput = getInputItem(INPUT_SLOT_0);
        ItemStack rightInput = getInputItem(INPUT_SLOT_1);

        if (!leftInput.isEmpty()) {
            AnvilUpdateEvent event = new AnvilUpdateEvent(leftInput, rightInput, this.itemName, getResultItem(),
                                                          getCost(), this.repairItemCountCost, player);
            // If the event is canceled, the anvil operation is void. Set the result to empty and the cost to zero.
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                this.setResultItem(ItemStack.EMPTY);
                this.setCost(0);
                this.repairItemCountCost = 0;
                return;
            }

            // Otherwise, update the results to the new values.
            this.setResultItem(event.getOutput());
            this.setCost(event.getXpCost());
            this.repairItemCountCost = event.getMaterialCost();
        }
    }

    protected boolean mayPickup(Player player, boolean hasItem) {
        return (player.hasInfiniteMaterials() || player.experienceLevel >= getCost()) && getCost() > 0;
    }

    protected void onTake(Player player, @NonNull ItemStack carried) {
        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-getCost());
        }

        ItemStack leftInput = getInputItem(INPUT_SLOT_0);
        ItemStack rightInput = getInputItem(INPUT_SLOT_1);

        if (this.repairItemCountCost > 0) {
            if (!rightInput.isEmpty() && rightInput.getCount() > this.repairItemCountCost) {
                rightInput.shrink(this.repairItemCountCost);
                setInputItem(INPUT_SLOT_1, rightInput);
            }
            else {
                setInputItem(INPUT_SLOT_1, ItemStack.EMPTY);
            }
        }
        else if (!this.onlyRenaming) {
            setInputItem(INPUT_SLOT_1, ItemStack.EMPTY);
        }

        setCost(0);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!StringUtil.isBlank(this.itemName) && !leftInput.getHoverName().getString().equals(this.itemName)) {
                serverPlayer.getTextFilter().processStreamMessage(this.itemName);
            }
        }

        setInputItem(INPUT_SLOT_0, ItemStack.EMPTY);

        // Anvil falling block damage
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
        ItemStack leftInput = getInputItem(INPUT_SLOT_0); // Left slot
        this.onlyRenaming = false;
        setCost(1);
        int price = 0;
        long tax = 0L;
        int namingCost = 0;
        if (!leftInput.isEmpty() && EnchantmentHelper.canStoreEnchantments(leftInput)) {
            ItemStack result = leftInput.copy();
            ItemStack rightInput = getInputItem(INPUT_SLOT_1); // Right slot
            ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(result));
            tax += (long) leftInput.getOrDefault(DataComponents.REPAIR_COST, 0) + (long) rightInput.getOrDefault(DataComponents.REPAIR_COST, 0);
            this.repairItemCountCost = 0;
            if (!rightInput.isEmpty()) {
                boolean usingBookRight = rightInput.has(DataComponents.STORED_ENCHANTMENTS);
                if (result.isDamageableItem() && leftInput.isValidRepairItem(rightInput)) {
                    int repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                    if (repairAmount <= 0) {
                        emptyResultSlot();
                        return;
                    }

                    int count;
                    for (count = 0; repairAmount > 0 && count < rightInput.getCount(); ++count) {
                        int resultDamage = result.getDamageValue() - repairAmount;
                        result.setDamageValue(resultDamage);
                        ++price;
                        repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                    }
                    this.repairItemCountCost = count;
                }
                else {
                    boolean isInvalidItem = result.is(Items.BOOK) || result.is(Items.ENCHANTED_BOOK);
                    // Enchanted item + Book
                    boolean isBook = (result.has(DataComponents.ENCHANTMENTS) && !isInvalidItem) &&
                                     (!usingBookRight && rightInput.is(Items.BOOK));
                    if (isBook) {
                        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.toImmutable().entrySet()) {
                            Holder<Enchantment> enchantmentHolder = entry.getKey();
                            int level =  entry.getIntValue();
                            book.enchant(enchantmentHolder, level);
                        }
                        EnchantmentHelper.setEnchantments(book, enchantments.toImmutable());
                        changedSlots(book); // book
                        return;
                    }

                    boolean isBookRight = !usingBookRight && (!result.is(rightInput.getItem()) || !result.isDamageableItem());
                    boolean isBookLeft = leftInput.has(DataComponents.ENCHANTABLE) && leftInput.is(Items.BOOK);
                    if (isBookRight || isBookLeft) {
                        emptyResultSlot();
                        return;
                    }

                    if (result.isDamageableItem() && !usingBookRight) {
                        int resultDamage = getResultDamage(leftInput, rightInput, result);

                        if (resultDamage < result.getDamageValue()) {
                            result.setDamageValue(resultDamage);
                            price += 2;
                        }
                    }

                    ItemEnchantments additionalEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(rightInput);
                    boolean isAnyEnchantmentCompatible = false;
                    boolean isAnyEnchantmentNotCompatible = false;

                    for (Object2IntMap.Entry<Holder<Enchantment>> entry : additionalEnchantments.entrySet()) {
                        Holder<Enchantment> enchantmentHolder = entry.getKey();
                        int current = enchantments.getLevel(enchantmentHolder);
                        int level = entry.getIntValue();
                        level = Integer.sum(current, level); // Sum current and level enchantments
                        Enchantment enchantment = enchantmentHolder.value();
                        boolean compatible = leftInput.supportsEnchantment(enchantmentHolder);
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
                            if (usingBookRight) {
                                fee = Math.max(1, fee / 2);
                            }

                            price += fee * level;
                            if (leftInput.getCount() > 1) {
                                price = 40;
                            }
                        }
                    }

                    if (isAnyEnchantmentNotCompatible && !isAnyEnchantmentCompatible) {
                        emptyResultSlot();
                        return;
                    }
                }
            }

            if (this.itemName != null && !StringUtil.isBlank(this.itemName)) {
                if (!this.itemName.equals(leftInput.getHoverName().getString())) {
                    namingCost = 1;
                    price += namingCost;
                    result.set(DataComponents.CUSTOM_NAME, Component.literal(this.itemName));
                }
            }
            else if (leftInput.has(DataComponents.CUSTOM_NAME)) {
                namingCost = 1;
                price += namingCost;
                result.remove(DataComponents.CUSTOM_NAME);
            }

            int finalPrice = price <= 0 ? 0 : (int) Mth.clamp(tax + (long)price, 0L, 2147483647L);
            setCost(finalPrice);
            if (price <= 0) {
                result = ItemStack.EMPTY;
            }

            if (namingCost == price && namingCost > 0) {
                if (getCost() >= 40) {
                    setCost(39);
                }
                this.onlyRenaming = true;
            }

            if (getCost() >= 40 && !this.player.hasInfiniteMaterials()) {
                result = ItemStack.EMPTY;
            }

            if (!result.isEmpty()) {
                int baseCost = result.getOrDefault(DataComponents.REPAIR_COST, 0);
                if (baseCost < rightInput.getOrDefault(DataComponents.REPAIR_COST, 0)) {
                    baseCost = rightInput.getOrDefault(DataComponents.REPAIR_COST, 0);
                }

                if (namingCost != price || namingCost == 0) {
                    baseCost = calculateIncreasedRepairCost(baseCost);
                }
                result.set(DataComponents.REPAIR_COST, baseCost);
                EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
            }
            changedSlots(result);
        }
        else {
            emptyResultSlot();
        }
    }

    private void changedSlots(@NonNull ItemStack stack) {
        setResultItem(stack);
        this.broadcastChanges();
    }

    private void emptyResultSlot() {
        setResultItem(ItemStack.EMPTY);
        this.cost.set(0);
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
        return (int) Math.min((long) baseCost * 2L + 1L, 2147483647L);
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
}