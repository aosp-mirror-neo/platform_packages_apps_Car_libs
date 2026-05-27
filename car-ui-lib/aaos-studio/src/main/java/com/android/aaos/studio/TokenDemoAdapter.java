/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.aaos.studio;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.oem.tokens.Token;

import java.util.List;

/**
 * A {@link RecyclerView.Adapter} that can display demo token values.
 */
public class TokenDemoAdapter extends
        RecyclerView.Adapter<TokenDemoAdapter.TokenDemoItemViewHolder> {

    static final int VIEW_TYPE_LIST_COLOR = 1;
    static final int VIEW_TYPE_LIST_TEXT = 2;
    static final int VIEW_TYPE_LIST_SHAPE = 3;

    public static class TokenItem {
        public final String name;
        public final int attrId;
        public final int type;

        public TokenItem(String name, int attrId, int type) {
            this.name = name;
            this.attrId = attrId;
            this.type = type;
        }
    }

    private final List<TokenItem> mItems;
    // Cached value to avoid repeated slow reads of oemShapeCornerFull token
    private Float mFullCornerRadius = null;

    public TokenDemoAdapter(List<TokenItem> items) {
        mItems = items;
    }

    @NonNull
    @Override
    public TokenDemoItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view;
        switch (viewType) {
            case VIEW_TYPE_LIST_COLOR:
                view = inflater.inflate(R.layout.token_list_item_color, parent, false);
                break;
            case VIEW_TYPE_LIST_TEXT:
                view = inflater.inflate(R.layout.token_list_item_text, parent, false);
                break;
            case VIEW_TYPE_LIST_SHAPE:
                view = inflater.inflate(R.layout.token_list_item_shape, parent, false);
                break;
            default:
                throw new IllegalArgumentException("Invalid view type: " + viewType);
        }
        return new TokenDemoItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TokenDemoItemViewHolder holder, int position) {
        TokenItem item = mItems.get(position);
        Context oemContext = Token.createOemStyledContext(holder.itemView.getContext());

        switch (holder.getItemViewType()) {
            case VIEW_TYPE_LIST_COLOR:
                holder.mText.setText(item.name);
                int color = Token.getColor(oemContext, item.attrId);
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.RECTANGLE);
                if (mFullCornerRadius == null) {
                    mFullCornerRadius = Token.getCornerRadius(oemContext,
                            R.attr.oemShapeCornerFull);
                }
                circle.setCornerRadius(mFullCornerRadius);
                circle.setColor(color);
                holder.mColorIndicator.setBackground(circle);
                holder.mText.setTextColor(Token.getColor(oemContext, R.attr.oemColorOnSurface));
                break;
            case VIEW_TYPE_LIST_TEXT:
                holder.mText.setText(item.name);
                int textAppearanceId = Token.getTextAppearance(oemContext,
                        item.attrId);
                holder.mText.setTextAppearance(textAppearanceId);
                break;
            case VIEW_TYPE_LIST_SHAPE:
                float cornerRadius = Token.getCornerRadius(oemContext,
                        item.attrId);
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.RECTANGLE);
                shape.setColor(Token.getColor(oemContext, R.attr.oemColorPrimary));
                shape.setCornerRadius(cornerRadius);
                holder.mColorIndicator.setBackground(shape);

                holder.mText.setText(item.name);

                break;
            default:
                throw new IllegalStateException("Unexpected value: " + holder.getItemViewType());
        }
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position).type;
    }


    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public static class TokenDemoItemViewHolder extends RecyclerView.ViewHolder {
        TextView mText;
        View mColorIndicator;

        TokenDemoItemViewHolder(@NonNull View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.textTitle);
            mColorIndicator = itemView.findViewById(R.id.color_indicator);
        }
    }
}
