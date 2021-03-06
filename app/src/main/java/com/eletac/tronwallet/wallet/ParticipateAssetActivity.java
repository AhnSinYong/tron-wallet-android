package com.eletac.tronwallet.wallet;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.arasthel.asyncjob.AsyncJob;
import com.eletac.tronwallet.CaptureActivityPortrait;
import com.eletac.tronwallet.InputFilterMinMax;
import com.eletac.tronwallet.R;
import com.eletac.tronwallet.Utils;
import com.eletac.tronwallet.block_explorer.BlockExplorerUpdater;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.yarolegovich.lovelydialog.LovelyInfoDialog;
import com.yarolegovich.lovelydialog.LovelyProgressDialog;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;
import com.yarolegovich.lovelydialog.LovelyTextInputDialog;

import org.spongycastle.util.encoders.DecoderException;
import org.spongycastle.util.encoders.Hex;
import org.tron.protos.Contract;
import org.tron.protos.Protocol;
import org.tron.walletserver.WalletClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

public class ParticipateAssetActivity extends AppCompatActivity {

    public static final String ASSET_NAME_EXTRA = "asset_name_extra";

    private TextView mName_TextView;
    private TextView mDescription_TextView;
    private TextView mSupply_TextView;
    private TextView mIssuer_TextView;
    private TextView mStart_TextView;
    private TextView mEnd_TextView;
    private TextView mPrice_TextView;

    private EditText mAmount_EditText;
    private TextView mCost_TextView;
    private Button mSpend_Button;

    private Contract.AssetIssueContract mAsset;
    private Protocol.Account mAccount;
    private boolean mIsPublicAddressOnly;
    private String mAddress;
    private double mTokenPrice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_participate_asset);

        mName_TextView = findViewById(R.id.ParticipateAsset_name_textView);
        mDescription_TextView = findViewById(R.id.ParticipateAsset_description_textView);
        mSupply_TextView = findViewById(R.id.ParticipateAsset_supply_textView);
        mIssuer_TextView = findViewById(R.id.ParticipateAsset_issuer_textView);
        mStart_TextView = findViewById(R.id.ParticipateAsset_start_textView);
        mEnd_TextView = findViewById(R.id.ParticipateAsset_end_textView);
        mPrice_TextView = findViewById(R.id.ParticipateAsset_price_textView);

        mAmount_EditText = findViewById(R.id.ParticipateAsset_amount_editText);
        mCost_TextView = findViewById(R.id.ParticipateAsset_cost_textView);
        mSpend_Button = findViewById(R.id.ParticipateAsset_spend_button);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String assetName = extras.getString(ASSET_NAME_EXTRA);
            if(assetName != null && !assetName.isEmpty()) {
                for(Contract.AssetIssueContract asset : BlockExplorerUpdater.getTokens()) {
                    if(asset.getName().toStringUtf8().equals(assetName)) {
                        mAsset = asset;
                        break;
                    }
                }
            } else {
                finish();
                return;
            }
        }

        if(mAsset != null) {

            mAccount = Utils.getAccount(this);
            mAddress = Utils.getPublicAddress(this);

            SharedPreferences sharedPreferences = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
            mIsPublicAddressOnly = sharedPreferences.getBoolean(getString(R.string.is_public_address_only), false);

            mName_TextView.setText(mAsset.getName().toStringUtf8());
            mDescription_TextView.setText(mAsset.getDescription().toStringUtf8());
            mSupply_TextView.setText(NumberFormat.getInstance(Locale.US).format(mAsset.getTotalSupply()));
            mIssuer_TextView.setText(WalletClient.encode58Check(mAsset.getOwnerAddress().toByteArray()));
            mStart_TextView.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US).format(new Date(mAsset.getStartTime())));
            mEnd_TextView.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US).format(new Date(mAsset.getEndTime())));

            mSpend_Button.setEnabled(false);
            NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
            numberFormat.setMaximumFractionDigits(6);

            mTokenPrice = mAsset.getTrxNum()/(double)(mAsset.getNum());
            mPrice_TextView.setText(numberFormat.format(mTokenPrice/1000000D));

            mAmount_EditText.setFilters(new InputFilter[]{ new InputFilterMinMax(0, mAccount.getBalance()/mTokenPrice)});

            mAmount_EditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if(mAmount_EditText.getText().length() > 0) {
                        NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
                        numberFormat.setMaximumFractionDigits(6);
                        double cost = (Long.parseLong(mAmount_EditText.getText().toString()) * mTokenPrice/1000000D);
                        mCost_TextView.setText(numberFormat.format(cost));
                        mSpend_Button.setEnabled(true);
                    } else {
                        mCost_TextView.setText("0");
                        mSpend_Button.setEnabled(false);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            mSpend_Button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    long amount = 0;
                    if(mAmount_EditText.getText().length() > 0) {
                        amount = Long.parseLong(mAmount_EditText.getText().toString());
                    } else {
                        return;
                    }
                    long finalAmount = (long) (amount*mTokenPrice);
                    if (mIsPublicAddressOnly) {
                        new LovelyStandardDialog(ParticipateAssetActivity.this)
                                .setTopColorRes(R.color.colorPrimary)
                                .setIcon(R.drawable.ic_info_white_24px)
                                .setTitle(R.string.confirm_spending)
                                .setPositiveButton(R.string.sign, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        try {
                                            Protocol.Transaction transaction = WalletClient.participateAssetIssueTransaction(
                                                    mAsset.getOwnerAddress().toByteArray(), mAsset.getName().toByteArray(), WalletClient.decodeFromBase58Check(mAddress), finalAmount);

                                            if (transaction == null || transaction.getRawData().getContractCount() == 0) {
                                                new LovelyInfoDialog(ParticipateAssetActivity.this)
                                                        .setTopColorRes(R.color.colorPrimary)
                                                        .setIcon(R.drawable.ic_error_white_24px)
                                                        .setTitle(R.string.spending_failed)
                                                        .setMessage(R.string.could_not_create_transaction)
                                                        .show();
                                            } else {
                                                Intent intent = new Intent(ParticipateAssetActivity.this, SignTransactionActivity.class);
                                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                                                transaction.writeTo(outputStream);
                                                outputStream.flush();

                                                intent.putExtra(SignTransactionActivity.TRANSACTION_DATA_EXTRA, outputStream.toByteArray());
                                                startActivity(intent);
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    } else {
                        new LovelyTextInputDialog(ParticipateAssetActivity.this, R.style.EditTextTintTheme)
                                .setTopColorRes(R.color.colorPrimary)
                                .setIcon(R.drawable.ic_info_white_24px)
                                .setTitle(R.string.confirm_spending)
                                .setHint(R.string.password)
                                .setInputType(InputType.TYPE_CLASS_TEXT |
                                        InputType.TYPE_TEXT_VARIATION_PASSWORD)
                                .setConfirmButtonColor(Color.WHITE)
                                .setConfirmButton(R.string.spend, new LovelyTextInputDialog.OnTextInputConfirmListener() {
                                    @Override
                                    public void onTextInputConfirmed(String text) {

                                        if (WalletClient.checkPassWord(text)) {

                                            LovelyProgressDialog progressDialog = new LovelyProgressDialog(ParticipateAssetActivity.this)
                                                    .setIcon(R.drawable.ic_send_white_24px)
                                                    .setTitle(R.string.spending)
                                                    .setTopColorRes(R.color.colorPrimary);
                                            progressDialog.show();

                                            AsyncJob.doInBackground(() -> {
                                                WalletClient walletClient = WalletClient.GetWalletByStorage(text);
                                                if (walletClient != null) {
                                                    boolean sent = false;
                                                    try {
                                                        sent = walletClient.participateAssetIssue(
                                                                mAsset.getOwnerAddress().toByteArray(),
                                                                mAsset.getName().toByteArray(),
                                                                finalAmount);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }

                                                    boolean finalSent = sent;
                                                    AsyncJob.doOnMainThread(() -> {
                                                        progressDialog.dismiss();

                                                        LovelyInfoDialog infoDialog = new LovelyInfoDialog(ParticipateAssetActivity.this)
                                                                .setTopColorRes(R.color.colorPrimary)
                                                                .setIcon(R.drawable.ic_send_white_24px);
                                                        if (finalSent) {
                                                            infoDialog.setTitle(R.string.spending_successfully);
                                                        } else {
                                                            infoDialog.setTitle(R.string.spending_failed);
                                                            infoDialog.setMessage(R.string.try_later);
                                                        }
                                                        infoDialog.show();
                                                        AccountUpdater.singleShot(3000);
                                                    });
                                                }
                                            });
                                        } else {
                                            new LovelyInfoDialog(ParticipateAssetActivity.this)
                                                    .setTopColorRes(R.color.colorPrimary)
                                                    .setIcon(R.drawable.ic_error_white_24px)
                                                    .setTitle(R.string.spending_failed)
                                                    .setMessage(R.string.wrong_password)
                                                    .show();
                                        }
                                    }
                                })
                                .setNegativeButtonColor(Color.WHITE)
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    }
                }
            });

        } else {
            finish();
        }
    }
}
