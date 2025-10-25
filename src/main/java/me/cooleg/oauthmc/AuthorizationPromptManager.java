package me.cooleg.oauthmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthorizationPromptManager {
    
    private final OauthMC plugin;
    private final Map<UUID, PromptSession> activePrompts = new ConcurrentHashMap<>();
    
    public AuthorizationPromptManager(OauthMC plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 开始发送授权提示
     * @param player 玩家
     * @param authUrl 授权链接
     * @param state 临时 token
     * @param isFirstTime 是否首次绑定
     */
    public void startPrompt(Player player, String authUrl, String state, boolean isFirstTime) {
        stopPrompt(player);
        
        Component baseMessage = isFirstTime ? 
            getFirstTimeBindingMessage(authUrl, state) : 
            getReturningPlayerMessage(authUrl, state);
        Component message = plugin.getOauthConfig().getServerName()
            .append(Component.text("\n\n"))
            .append(baseMessage);
        
        int initialDelay = Math.max(0, getConfig().getInt("oauth.prompt.initial_delay_seconds", 1)) * 20;
        BukkitTask initialTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sendTitlePrompt(player, isFirstTime);
                player.sendMessage(message);
                playSound(player);
            }
        }, initialDelay);
        
        int repeatInterval = Math.max(1, getConfig().getInt("oauth.prompt.repeat_interval_seconds", 10)) * 20;
        long repeatInitialDelay = (long) initialDelay + repeatInterval;
        BukkitTask repeatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopPrompt(player);
                return;
            }
            
            player.sendMessage(message);
            playSound(player);
            
        }, repeatInitialDelay, repeatInterval);
        
        activePrompts.put(player.getUniqueId(), new PromptSession(initialTask, repeatTask));
    }
    
    /**
     * 停止发送授权提示
     */
    public void stopPrompt(Player player) {
        PromptSession session = activePrompts.remove(player.getUniqueId());
        if (session != null) {
            session.cancel();
        }
    }
    
    /**
     * 停止所有提示（插件禁用时调用）
     */
    public void stopAllPrompts() {
        activePrompts.values().forEach(PromptSession::cancel);
        activePrompts.clear();
    }
    
    private Component getFirstTimeBindingMessage(String authUrl, String state) {
        return buildPromptMessage(
            "⚠ 首次登录需要绑定 linux.do 账号",
            "请点击下方链接完成授权：",
            "[🔗 点击授权]",
            authUrl,
            state
        );
    }
    
    private Component getReturningPlayerMessage(String authUrl, String state) {
        return buildPromptMessage(
            "🔒 需要验证 linux.do 身份",
            "请点击下方链接完成验证：",
            "[🔗 点击验证]",
            authUrl,
            state
        );
    }
    
    private Component buildPromptMessage(String header, String actionLine, String buttonText, String authUrl, String state) {
        StringBuilder builder = new StringBuilder();
        builder.append("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>\n")
                .append("<yellow><bold>").append(header).append("</bold></yellow>\n\n")
                .append("<gray>").append(actionLine).append("</gray>\n")
                .append("<click:open_url:'").append(authUrl).append("'><green><bold>").append(buttonText).append("</bold></green></click>\n\n")
                .append("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>\n");

        if (state != null && !state.isBlank()) {
            builder.append("<gray>• 授权代码：<yellow>").append(state).append("</yellow></gray>\n");
        }

        builder.append("<gray>• 授权有效期：<yellow>10分钟</yellow></gray>\n")
                .append("<gray>• 此消息每 <yellow>10秒</yellow> 重复提示</gray>\n")
                .append("<gold>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gold>");

        return MiniMessage.miniMessage().deserialize(builder.toString());
    }
    
    private void sendTitlePrompt(Player player, boolean isFirstTime) {
        Component title = MiniMessage.miniMessage().deserialize(
            isFirstTime ? 
            "<yellow><bold>请绑定 linux.do 账号</bold></yellow>" :
            "<yellow><bold>请验证 linux.do 身份</bold></yellow>"
        );
        
        Component subtitle = MiniMessage.miniMessage().deserialize(
            "<gray>查看聊天框中的授权链接</gray>"
        );
        
        player.showTitle(Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofSeconds(5),
                Duration.ofMillis(1000)
            )
        ));
    }
    
    private void playSound(Player player) {
        if (getConfig().getBoolean("oauth.prompt.enable_sound", true)) {
            String soundName = getConfig().getString("oauth.prompt.sound_type", "BLOCK_NOTE_BLOCK_PLING");
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 0.8f, 1.2f);
            } catch (IllegalArgumentException e) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
            }
        }
    }
    
    private FileConfiguration getConfig() {
        return plugin.getConfig();
    }
    
    private static class PromptSession {
        private final BukkitTask initialTask;
        private final BukkitTask repeatTask;
        
        public PromptSession(BukkitTask initialTask, BukkitTask repeatTask) {
            this.initialTask = initialTask;
            this.repeatTask = repeatTask;
        }
        
        public void cancel() {
            if (initialTask != null && !initialTask.isCancelled()) {
                initialTask.cancel();
            }
            if (repeatTask != null && !repeatTask.isCancelled()) {
                repeatTask.cancel();
            }
        }
    }
}
