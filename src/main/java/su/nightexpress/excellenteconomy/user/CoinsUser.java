package su.nightexpress.excellenteconomy.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.jspecify.annotations.NonNull;

import su.nightexpress.excellenteconomy.api.currency.ExcellentCurrency;
import su.nightexpress.excellenteconomy.api.event.ChangeBalanceEvent;
import su.nightexpress.excellenteconomy.user.data.CurrencySettings;
import su.nightexpress.nightcore.user.UserTemplate;

public class CoinsUser extends UserTemplate {

    private final UserBalance                   balance;
    private final Map<String, CurrencySettings> settingsMap;

    private long    lastSeen;
    private boolean hiddenFromTops;

    public CoinsUser(@NonNull UUID uuid,
                     @NonNull String name,
                     @NonNull UserBalance balance,
                     @NonNull Map<String, CurrencySettings> settingsMap,
                     long lastSeen,
                     boolean hiddenFromTops) {
        super(uuid, name);
        this.balance = balance;
        this.settingsMap = new HashMap<>(settingsMap);

        this.setLastSeen(lastSeen);
        this.setHiddenFromTops(hiddenFromTops);
    }

    @NonNull
    public UserBalance getBalance() {
        return this.balance;
    }

    /**
     * Edits user's balance of specific currency and fires the ChangeBalanceEvent event. If event was cancelled, the
     * balance is set back to previous (old) value.
     *
     * @param currency Currency to edit balance of.
     * @param consumer balance function.
     */
    public void editBalance(@NonNull ExcellentCurrency currency, @NonNull Consumer<UserBalance> consumer) {
        double oldBalance = this.getBalance(currency);

        consumer.accept(this.balance);

        ChangeBalanceEvent event = new ChangeBalanceEvent(this, currency, oldBalance, this.getBalance(currency));
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            this.balance.set(currency, oldBalance);
        }
    }

    public void resetBalance(@NonNull Collection<ExcellentCurrency> currencies) {
        currencies.forEach(this::resetBalance);
    }

    public void resetBalance(@NonNull ExcellentCurrency currency) {
        this.editBalance(currency, bal -> bal.set(currency, currency.getStartValue()));
    }

    public boolean hasEnough(@NonNull ExcellentCurrency currency, double amount) {
        return this.balance.has(currency, amount);
    }

    public double getBalance(@NonNull ExcellentCurrency currency) {
        return this.balance.get(currency);
    }

    public void addBalance(@NonNull ExcellentCurrency currency, double amount) {
        this.editBalance(currency, bal -> bal.add(currency, amount));
    }

    public void removeBalance(@NonNull ExcellentCurrency currency, double amount) {
        this.editBalance(currency, lookup -> lookup.remove(currency, amount));
    }

    /**
     * Atomically withdraws the amount only if the balance covers it, then fires the ChangeBalanceEvent.
     * If the event is cancelled, the amount is returned to the balance.
     *
     * @return true if the amount was actually withdrawn.
     */
    public boolean tryRemoveBalance(@NonNull ExcellentCurrency currency, double amount) {
        double toRemove = Math.abs(amount);
        if (!this.balance.tryRemove(currency, toRemove)) return false;

        double newBalance = this.getBalance(currency);
        ChangeBalanceEvent event = new ChangeBalanceEvent(this, currency, newBalance + toRemove, newBalance);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            this.balance.add(currency, toRemove);
            return false;
        }
        return true;
    }

    /**
     * Adds the amount and fires the ChangeBalanceEvent.
     *
     * @return true if the deposit went through, false if the event was cancelled (balance restored).
     */
    public boolean tryAddBalance(@NonNull ExcellentCurrency currency, double amount) {
        double toAdd = Math.abs(amount);
        double oldBalance = this.getBalance(currency);

        this.balance.add(currency, toAdd);

        ChangeBalanceEvent event = new ChangeBalanceEvent(this, currency, oldBalance, this.getBalance(currency));
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            this.balance.remove(currency, toAdd);
            return false;
        }
        return true;
    }

    public void setBalance(@NonNull ExcellentCurrency currency, double amount) {
        this.editBalance(currency, lookup -> lookup.set(currency, amount));
    }

    @NonNull
    public Map<String, CurrencySettings> getSettingsMap() {
        return this.settingsMap;
    }

    @NonNull
    public CurrencySettings getSettings(@NonNull ExcellentCurrency currency) {
        return this.settingsMap.computeIfAbsent(currency.getId(), k -> CurrencySettings.create(currency));
    }

    public long getLastSeen() {
        return this.lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isHiddenFromTops() {
        return this.hiddenFromTops;
    }

    public void setHiddenFromTops(boolean hiddenFromTops) {
        this.hiddenFromTops = hiddenFromTops;
    }
}
