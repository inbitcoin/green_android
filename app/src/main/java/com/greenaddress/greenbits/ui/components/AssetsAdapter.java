package com.greenaddress.greenbits.ui.components;

import android.app.Activity;
import android.content.res.Resources;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenaddress.greenapi.data.AssetInfoData;
import com.greenaddress.greenapi.data.BalanceData;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.ui.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.greenaddress.gdk.GDKSession.getSession;

public class AssetsAdapter extends RecyclerView.Adapter<AssetsAdapter.Item> {

    private static final ObjectMapper mObjectMapper = new ObjectMapper();
    private final Map<String, BalanceData> mAssets;
    private final List<String> mAssetsIds;
    private final OnAssetSelected mOnAccountSelected;
    private final GaService mService;

    @FunctionalInterface
    public interface OnAssetSelected {
        void onAssetSelected(String assetSelected);
    }

    public AssetsAdapter(final Map<String, BalanceData> assets, final GaService service,
                         final OnAssetSelected cb) {
        mAssets = assets;
        mService = service;
        mOnAccountSelected = cb;
        mAssetsIds = new ArrayList<>(mAssets.keySet());
        if (mAssetsIds.contains("btc")) {
            // Move btc as first in the list
            mAssetsIds.remove("btc");
            mAssetsIds.add(0,"btc");
        }
    }

    @Override
    public Item onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                          .inflate(R.layout.list_element_asset, parent, false);
        return new Item(view);
    }

    @Override
    public void onBindViewHolder(final Item holder, final int position) {
        final String assetId = mAssetsIds.get(position);
        holder.mAssetLayout.setOnClickListener(v -> mOnAccountSelected.onAssetSelected(assetId));
        final BalanceData balanceData = mAssets.get(assetId);
        final AssetInfoData assetInfo = balanceData.getAssetInfo() !=
                                        null ? balanceData.getAssetInfo() : new AssetInfoData(assetId, assetId, 0, "",
                                                                                              "");
        try {
            final ObjectNode details = mObjectMapper.createObjectNode();
            details.put("satoshi", balanceData.getSatoshi());
            details.set("asset_info", assetInfo.toObjectNode());
            final ObjectNode converted = getSession().convert(details);
            final String amount = converted.get(assetId).asText();
            holder.mAssetName.setText("btc".equals(assetId) ? "L-BTC" : assetInfo.getName());
            if ("btc".equals(assetId) ) {
                holder.mAssetValue.setText(mService.getValueString(converted,false,true));
            } else {
                holder.mAssetValue.setText(String.format("%s %s", amount, assetInfo.getTicker()));
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return mAssets.size();
    }

    static class Item extends RecyclerView.ViewHolder {

        final LinearLayout mAssetLayout;
        final TextView mAssetName;
        final TextView mAssetValue;

        Item(final View v) {
            super(v);
            mAssetLayout = v.findViewById(R.id.assetLayout);
            mAssetName = v.findViewById(R.id.assetName);
            mAssetValue = v.findViewById(R.id.assetValue);
        }
    }
}
