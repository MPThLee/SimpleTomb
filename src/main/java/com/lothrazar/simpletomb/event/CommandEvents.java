package com.lothrazar.simpletomb.event;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.lothrazar.simpletomb.ModTomb;
import com.lothrazar.simpletomb.TombRegistry;
import com.lothrazar.simpletomb.block.TileEntityTomb;
import com.lothrazar.simpletomb.data.LocationBlockPos;
import com.lothrazar.simpletomb.data.PlayerTombRecords;
import com.lothrazar.simpletomb.data.TombCommands;
import com.lothrazar.simpletomb.helper.WorldHelper;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class CommandEvents {

  private static final String ARG_SELECTED = "selected";
  private static final String ARG_PLAYER = "player";

  @SubscribeEvent
  public void onRegisterCommandsEvent(RegisterCommandsEvent event) {
    CommandDispatcher<CommandSourceStack> r = event.getDispatcher();
    r.register(LiteralArgumentBuilder.<CommandSourceStack> literal(ModTomb.MODID)
        .requires((p) -> {
          return p.hasPermission(3);
        })
        .then(Commands.literal(TombCommands.RESTORE.toString())
            .then(Commands.argument(ARG_PLAYER, GameProfileArgument.gameProfile()).suggests((cs, b) -> buildPlayerArg(cs, b))
                .then(Commands.argument(ARG_SELECTED, IntegerArgumentType.integer())
                    .executes(x -> {
                      return exeRestore(x, getPlayerProfile(x), IntegerArgumentType.getInteger(x, ARG_SELECTED));
                    }))))
        .then(Commands.literal(TombCommands.KEY.toString())
            .then(Commands.argument(ARG_PLAYER, GameProfileArgument.gameProfile()).suggests((cs, b) -> buildPlayerArg(cs, b))
                .then(Commands.argument(ARG_SELECTED, IntegerArgumentType.integer())
                    .executes(x -> {
                      return exeKey(x, getPlayerProfile(x), IntegerArgumentType.getInteger(x, ARG_SELECTED));
                    }))))
        .then(Commands.literal(TombCommands.LIST.toString())
            .then(Commands.argument(ARG_PLAYER, GameProfileArgument.gameProfile()).suggests((cs, b) -> buildPlayerArg(cs, b))
                .executes(x -> {
                  return exeList(x, getPlayerProfile(x));
                })))
        .then(Commands.literal(TombCommands.DELETE.toString())
            .then(Commands.argument(ARG_PLAYER, GameProfileArgument.gameProfile()).suggests((cs, b) -> buildPlayerArg(cs, b))
                .executes(x -> {
                  return exeDelete(x, getPlayerProfile(x));
                })))
    // more go here
    );
  }

  private CompletableFuture<Suggestions> buildPlayerArg(CommandContext<CommandSourceStack> cs, SuggestionsBuilder b) {
    return SharedSuggestionProvider.suggest(cs.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getGameProfile().getName()), b);
  }

  private GameProfile getPlayerProfile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    return GameProfileArgument.getGameProfiles(ctx, ARG_PLAYER).stream().findFirst().orElse(null);
  }

  private int exeDelete(CommandContext<CommandSourceStack> ctx, GameProfile target) throws CommandSyntaxException {
    ServerPlayer user = ctx.getSource().getPlayerOrException();
    PlayerTombRecords found = ModTomb.GLOBAL.findGrave(target.getId());
    if (found != null) {
      int previous = found.playerGraves.size();
      found.deleteAll();
      TranslatableComponent msg = new TranslatableComponent("Deleted: " + previous);
      msg.setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
      user.sendMessage(msg, user.getUUID());
    }
    return 0;
  }

  private int exeList(CommandContext<CommandSourceStack> ctx, GameProfile target) throws CommandSyntaxException {
    ServerPlayer user = ctx.getSource().getPlayerOrException();
    PlayerTombRecords found = ModTomb.GLOBAL.findGrave(target.getId());
    if (found != null && found.playerGraves.size() > 0) {
      for (int i = 0; i < found.playerGraves.size(); i++) {
        TranslatableComponent msg = new TranslatableComponent(found.toDisplayString(i));
        msg.setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
        user.sendMessage(msg, user.getUUID());
      }
    }
    else {
      TranslatableComponent msg = new TranslatableComponent("Found: #0");
      msg.setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
      user.sendMessage(msg, user.getUUID());
    }
    return 0;
  }

  private int exeKey(CommandContext<CommandSourceStack> ctx, GameProfile target, int index) throws CommandSyntaxException {
    ServerPlayer user = ctx.getSource().getPlayerOrException();
    PlayerTombRecords found = ModTomb.GLOBAL.findGrave(target.getId());
    if (found != null) {
      CompoundTag grave = found.playerGraves.get(index);
      if (grave == null) {
        ModTomb.LOGGER.error("Invalid grave index " + index + "; try between 0 and  " + (found.playerGraves.size() - 1));
        return 1;
      }
      LocationBlockPos spawnPos = new LocationBlockPos(PlayerTombRecords.getPos(grave), PlayerTombRecords.getDim(grave));
      ItemStack key = new ItemStack(TombRegistry.GRAVE_KEY);
      TombRegistry.GRAVE_KEY.setTombPos(key, spawnPos);
      PlayerTombEvents.putKeyName(target.getName(), key);
      // key for u
      ItemHandlerHelper.giveItemToPlayer(user, key);
    }
    return 0;
  }

  private int exeRestore(CommandContext<CommandSourceStack> ctx, GameProfile target, int index) throws CommandSyntaxException {
    ServerPlayer user = ctx.getSource().getPlayerOrException();
    TranslatableComponent msg = new TranslatableComponent("Attempting to restore tomb [" + index + "] for player " + target.getName() + ":" + target.getId());
    user.sendMessage(msg, user.getUUID());
    PlayerTombRecords found = ModTomb.GLOBAL.findGrave(target.getId());
    if (found != null) {
      CompoundTag grave = found.playerGraves.get(index);
      if (grave == null) {
        ModTomb.LOGGER.error("Invalid grave index " + index + "; try between 0 and  " + (found.playerGraves.size() - 1));
        return 1;
      }
      BlockPos pos = PlayerTombRecords.getPos(grave);
      String dim = PlayerTombRecords.getDim(grave);
      //      ModTomb.LOGGER.error("found  at" + pos + " in " + dim);
      List<ItemStack> drops = PlayerTombRecords.getDrops(grave);
      //      ModTomb.LOGGER.error("items contained " + drops.size());
      //TODO: is this dupe code from location class?
      ResourceKey<Level> dimKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, ResourceLocation.tryParse(dim));
      ServerLevel targetWorld = ctx.getSource().getLevel().getServer().getLevel(dimKey);
      BlockState state = PlayerTombEvents.getRandomGrave(targetWorld, Direction.NORTH);
      boolean wasPlaced = WorldHelper.placeGrave(targetWorld, pos, state);
      if (wasPlaced) {
        //fill it up
        TileEntityTomb tile = (TileEntityTomb) targetWorld.getBlockEntity(pos);
        tile.initTombstoneOwner(target);
        IItemHandler itemHandler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null).orElse(null);
        //        ItemHandlerHelper.ins
        for (ItemStack d : drops) {
          ItemHandlerHelper.insertItemStacked(itemHandler, d.copy(), false);
        }
      }
      msg = new TranslatableComponent("Restored tomb with at [" + pos + "] in " + dim);
      user.sendMessage(msg, user.getUUID());
    }
    return 0;
  }
  //
  //  private void badCommandMsg(ServerPlayerEntity player) {
  //    //.setStyle(Style.EMPTY.setFormatting(TextFormatting.GOLD))
  //    player.sendMessage(new TranslationTextComponent(ModTomb.MODID + ".commands.null"), player.getUniqueID());
  //    player.sendMessage(new TranslationTextComponent("[" + String.join(", ", SUBCOMMANDS) + "]"), player.getUniqueID());
  //  }
}
