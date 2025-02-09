package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenaddress.gdk.GDKTwoFactorCall;
import com.greenaddress.greenapi.ConnectionManager;
import com.greenaddress.greenapi.data.AssetInfoData;
import com.greenaddress.greenapi.data.HWDeviceData;
import com.greenaddress.greenapi.data.SubaccountData;
import com.greenaddress.greenbits.ui.components.CharInputFilter;
import com.greenaddress.greenbits.ui.components.SwipeButton;

import java.io.IOException;
import java.util.ArrayList;

import static com.greenaddress.gdk.GDKSession.getSession;

public class SendConfirmActivity extends LoggedActivity implements SwipeButton.OnActiveListener {
    private static final String TAG = SendConfirmActivity.class.getSimpleName();

    private HWDeviceData mHwData;
    private ObjectNode mTxJson;
    private SwipeButton mSwipeButton;
    private AssetInfoData mAssetInfo;

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {
        if (mService == null || mService.getModel() == null) {
            toFirst();
            return;
        }
        try {

            final String hwwJson = getIntent().getStringExtra("hww");
            final ObjectMapper mObjectMapper = new ObjectMapper();
            mObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            if (hwwJson != null)
                mHwData = mObjectMapper.readValue(hwwJson, HWDeviceData.class);
            mTxJson =  mObjectMapper.readValue(getIntent().getStringExtra("transaction"), ObjectNode.class);
            mAssetInfo = (AssetInfoData) getIntent().getSerializableExtra("asset_info");
        } catch (final Exception e) {
            e.printStackTrace();
            UI.toast(this, e.getLocalizedMessage(), Toast.LENGTH_LONG);
            setResult(Activity.RESULT_CANCELED);
            finishOnUiThread();
            return;
        }

        // Bin Ui views
        setContentView(R.layout.activity_send_confirm);
        UI.preventScreenshots(this);
        setTitleBackTransparent();
        final TextView noteTextTitle = UI.find(this, R.id.sendMemoTitle);
        final TextView noteText = UI.find(this, R.id.noteText);
        final TextView addressText = UI.find(this, R.id.addressText);
        final TextView subaccountText = UI.find(this, R.id.subaccountText);
        mSwipeButton = UI.find(this, R.id.swipeButton);

        // Setup views fields
        final JsonNode address = mTxJson.withArray("addressees").get(0);
        final String currentRecipient = address.get("address").asText();
        final boolean isSweeping = mTxJson.get("is_sweep").asBoolean();
        final Integer subaccount = mTxJson.get("subaccount").asInt();
        UI.hideIf(isSweeping, noteTextTitle);
        UI.hideIf(isSweeping, noteText);
        if (isSweeping)
            subaccountText.setText(R.string.id_sweep_from_paper_wallet);
        else {
            final int subAccount = subaccount;
            final SubaccountData subaccountData =
                mService.getModel().getSubaccountDataObservable().getSubaccountDataWithPointer(subAccount);
            subaccountText.setText(subaccountData.getNameWithDefault(getString(R.string.id_main_account)));
        }
        addressText.setText(currentRecipient);
        noteText.setText(mTxJson.get("memo") == null ? "" : mTxJson.get("memo").asText());
        CharInputFilter.setIfNecessary(noteText);

        // Set currency & amount
        final long amount = mTxJson.get("satoshi").asLong();
        final long fee = mTxJson.get("fee").asLong();
        final TextView sendAmount = UI.find(this, R.id.sendAmount);
        final TextView sendFee = UI.find(this, R.id.sendFee);
        final JsonNode assetTag = address.get("asset_tag");
        if (mService.isLiquid()) {
            sendAmount.setVisibility(View.GONE);
            UI.find(this, R.id.amountWordSending).setVisibility(View.GONE); // hide the little "Amount" above the card

            final CardView assetCardview = findViewById(R.id.txAssetCard);
            final TextView txAssetText = assetCardview.findViewById(R.id.assetName);
            final TextView txAssetValue = assetCardview.findViewById(R.id.assetValue);
            final String asset = assetTag.asText();
            final AssetInfoData assetInfo = mAssetInfo !=
                                            null ? mAssetInfo : new AssetInfoData(asset, asset, 0, "", "");
            final ObjectMapper mapper = new ObjectMapper();
            final ObjectNode convert = mapper.createObjectNode();
            convert.set("asset_info", assetInfo.toObjectNode());
            convert.put("satoshi", address.get("satoshi").asLong());
            try {
                final ObjectNode converted = getSession().convert(convert);
                final String value =
                    "btc".equals(asset) ? mService.getValueString(converted,false,
                                                                  false) : converted.get(asset).asText();
                final String ticker = assetInfo.getTicker() != null ? assetInfo.getTicker() : "";
                txAssetText.setText("btc".equals(asset) ? mService.getBitcoinOrLiquidUnit() : assetInfo.getName());
                txAssetValue.setText(String.format("%s %s", value,
                                                   "btc".equals(asset) ? mService.getBitcoinOrLiquidUnit() : ticker));
                assetCardview.setVisibility(View.VISIBLE);
            } catch (final IOException e) {
                e.printStackTrace();
            }
        } else {
            sendAmount.setText(getFormatAmount(amount));
        }
        sendFee.setText(getFormatAmount(fee));

        if (mHwData != null && !mTxJson.get("change_address").isNull() && !mTxJson.get("change_amount").isNull()) {
            UI.show(UI.find(this, R.id.changeLayout));
            final TextView view = UI.find(this, R.id.changeAddressText);
            // FIXME: HWs are not supported (yet) on Liquid, hardcoding BTC here
            view.setText(mTxJson.get("change_address").get("btc").get("address").asText());
        }

        mSwipeButton.setOnActiveListener(this);
    }

    private String getFormatAmount(final long amount) {
        try {
            final ObjectNode feeNode = getSession().convertSatoshi(amount);
            return String.format("%s / %s",
                                 mService.getValueString(feeNode, false, true),
                                 mService.getValueString(feeNode, true, true));
        } catch (final RuntimeException | IOException e) {
            Log.e(TAG, "Conversion error: " + e.getLocalizedMessage());
            return "";
        }
    }

    @Override
    public void onActive() {
        startLoading();
        mSwipeButton.setEnabled(false);
        final TextView noteText = UI.find(this, R.id.noteText);
        final String memo = noteText.getText().toString();
        final GaActivity activity = SendConfirmActivity.this;
        mService.getExecutor().execute(() -> {
            try {
                // mTxJson.set("memo", new TextNode(memo));
                // sign transaction
                final ConnectionManager cm = mService.getConnectionManager();
                final GDKTwoFactorCall signCall = getSession().signTransactionRaw(activity, mTxJson);
                mTxJson = signCall.resolve(null, cm.getHWResolver());

                // send transaction
                final boolean isSweep = mTxJson.get("is_sweep").asBoolean();
                if (isSweep) {
                    getSession().broadcastTransactionRaw(mTxJson.get("transaction").asText());
                } else {
                    final GDKTwoFactorCall sendCall = getSession().sendTransactionRaw(activity, mTxJson);
                    sendCall.resolve(new PopupMethodResolver(activity), new PopupCodeResolver(activity));
                    mService.getModel().getTwoFactorConfigDataObservable().refresh();
                }
                if (mTxJson.has("previous_transaction")) {
                    //emptying list to avoid showing replaced txs
                    mService.getModel().getTransactionDataObservable(mTxJson.get("subaccount").asInt())
                    .setTransactionDataList(new ArrayList<>());
                    final String hash = mTxJson.get("previous_transaction").get("txhash").asText();
                    mService.getModel().getEventDataObservable().removeTx(hash);
                }
                UI.toast(activity, R.string.id_transaction_sent, Toast.LENGTH_LONG);

                activity.setResult(Activity.RESULT_OK);
                activity.finishOnUiThread();
            } catch (final Exception e) {
                final Resources res = getResources();
                final String msg = UI.i18n(res, e.getMessage());
                e.printStackTrace();
                UI.toast(activity, msg, Toast.LENGTH_LONG);
                if (msg.equals(res.getString(R.string.id_transaction_already_confirmed))) {
                    activity.setResult(Activity.RESULT_OK);
                    activity.finishOnUiThread();
                    return;
                }
                activity.runOnUiThread(() -> {
                    mSwipeButton.setEnabled(true);
                    mSwipeButton.moveButtonBack();
                    activity.stopLoading();
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            onBackPressed();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
