package com.game.econ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-owner item store with deterministic insertion order.
 *
 * <p>Items are keyed by {@link Item#id()} inside a {@link LinkedHashMap} so
 * that iteration always reflects insertion order — no HashMap nondeterminism.
 *
 * <p>GDD Part 7/8 — Phase 2 economy, econ-core lane.
 */
public final class Inventory {

    private final String ownerId;
    /** Keyed by item id; LinkedHashMap guarantees insertion-order iteration. */
    private final LinkedHashMap<String, Item> items = new LinkedHashMap<>();

    public Inventory(String ownerId) {
        this.ownerId = ownerId;
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Add {@code item} to this inventory.
     *
     * <p>If an item with the same {@code id} is already present it is replaced
     * (same deterministic position in insertion order).
     *
     * @param item the item to add (must not be {@code null})
     */
    public void add(Item item) {
        items.put(item.id(), item);
    }

    /**
     * Remove {@code item} from this inventory.
     *
     * @param item the item to remove
     * @return {@code true} if the item was present and has been removed
     */
    public boolean remove(Item item) {
        return items.remove(item.id()) != null;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Return {@code true} if this inventory holds an item with the same
     * {@link Item#id()} as the given item.
     *
     * @param item the item to test
     * @return {@code true} if present
     */
    public boolean contains(Item item) {
        return items.containsKey(item.id());
    }

    /**
     * Return an unmodifiable snapshot of all items in insertion order.
     *
     * @return unmodifiable list; never {@code null}
     */
    public List<Item> list() {
        return Collections.unmodifiableList(new ArrayList<>(items.values()));
    }

    /** @return the owner identifier supplied at construction */
    public String ownerId() {
        return ownerId;
    }
}
