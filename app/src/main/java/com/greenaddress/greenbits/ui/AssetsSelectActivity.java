package com.greenaddress.greenbits.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;

import com.greenaddress.greenapi.data.BalanceData;
import com.greenaddress.greenbits.ui.components.AssetsAdapter;
import com.greenaddress.greenbits.ui.preferences.PrefKeys;

import java.util.Map;

import static com.greenaddress.gdk.GDKSession.getSession;

public class AssetsSelectActivity extends LoggedActivity implements AssetsAdapter.OnAssetSelected {

    private RecyclerView assetsList;
    private Map<String, BalanceData> mAssetsBalances;

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {
        if (mService == null || mService.getModel() == null) {
            toFirst();
            return;
        }
        setTitleBackTransparent();
        setContentView(R.layout.activity_assets_selection);

        final String callingActivity = getCallingActivity() != null ? getCallingActivity().getClassName() : "";
        if (callingActivity.equals(TabbedMainActivity.class.getName())) {
            final String accountName = getModel().getSubaccountDataObservable().getSubaccountDataWithPointer(
                getModel().getCurrentSubaccount()).getNameWithDefault(getString(R.string.id_main_account));
            setTitle(accountName);
        } else if (callingActivity.equals(SendAmountActivity.class.getName())) {
            setTitle(R.string.id_select_asset);
        }

        assetsList = findViewById(R.id.assetsList);
        assetsList.setLayoutManager(new LinearLayoutManager(this));
        try {
            mAssetsBalances = getModel().getCurrentAccountBalanceData();

            final AssetsAdapter adapter = new AssetsAdapter(mAssetsBalances, mService, this);
            assetsList.setAdapter(adapter);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            setResult(RESULT_CANCELED);
            onBackPressed();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResumeWithService() {
        super.onResumeWithService();
        if (mService == null || mService.getModel() == null)
            return;
        if (mService.isDisconnected()) {
            return;
        }
    }

    @Override
    protected void onPauseWithService() {
        super.onPauseWithService();
        if (mService == null || mService.getModel() == null)
            return;
    }

    @Override
    public void onAssetSelected(final String assetId) {
        Log.d("ASSET", "selected " + assetId);
        if (getCallingActivity() !=
            null && getCallingActivity().getClassName().equals(TabbedMainActivity.class.getName()) ) {
            if ("btc".equals(assetId))
                return;
            final Intent intent = new Intent(AssetsSelectActivity.this, AssetActivity.class);
            intent.putExtra("ASSET_ID", assetId)
            .putExtra("ASSET_INFO", mAssetsBalances.get(assetId).getAssetInfo())
            .putExtra("SATOSHI", mAssetsBalances.get(assetId).getSatoshi());
            startActivity(intent);
            return;
        }
        final Intent intent = getIntent();
        intent.putExtra(PrefKeys.ASSET_SELECTED, assetId);
        setResult(RESULT_OK, intent);
        finish();
    }
}