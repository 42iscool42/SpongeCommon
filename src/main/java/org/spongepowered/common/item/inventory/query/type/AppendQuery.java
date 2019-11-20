/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.item.inventory.query.type;

import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.query.Query;
import org.spongepowered.common.item.inventory.EmptyInventoryImpl;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.query.SpongeQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppendQuery extends SpongeQuery {

    private final List<Query> queryList;

    public AppendQuery(List<Query> queryList) {
        this.queryList = Collections.unmodifiableList(queryList);
    }

    public static Query of(Query query, Query[] queries) {
        List<Query> newQueries = new ArrayList<>();
        if (query instanceof AppendQuery) {
            newQueries.addAll(((AppendQuery) query).queryList);
        } else {
            newQueries.add(query);
        }
        newQueries.addAll(Arrays.asList(queries));
        return new OrQuery(newQueries);
    }

    @Override
    public Inventory execute(InventoryAdapter inventory) {
        Inventory result = new EmptyInventoryImpl((Inventory) inventory);
        if (this.queryList.isEmpty()) {
            return result;
        }
        for (Query operation : this.queryList) {
            result = result.union(((Inventory) inventory).query(operation));
        }
        return result;
    }


}