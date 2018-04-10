package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;


public class AddWalletActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_wallet);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setupActionBar();

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.addWalletContent);
        contentView.removeAllViews();
        Bundle extras = getIntent().getExtras();
        if(extras != null && extras.containsKey("Create"))
        {

        }
        else
            inflater.inflate(R.layout.add_wallet_buttons, contentView);
    }

    private void setupActionBar()
    {
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_black_24dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_add_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }
    }

    public void focusOnText(int pTextID)
    {
        EditText text = findViewById(pTextID);
        if(text.requestFocus())
        {
            InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(inputManager != null)
                inputManager.showSoftInput(text, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void onButtonClick(View pView)
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.addWalletContent);

        switch(pView.getId())
        {
        case R.id.createNewWallet:
            break;
        case R.id.recoverWallet:
            break;
        case R.id.importBIP32Key: // Show dialog for entering BIP-0032 encoded key
        {
            contentView.removeAllViews();
            inflater.inflate(R.layout.import_bip32_key, contentView);
            focusOnText(R.id.importText);

            Spinner derivationMethod = findViewById(R.id.derivationMethodSpinner);
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
              R.array.derivation_methods, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            derivationMethod.setAdapter(adapter);
            break;
        }
        case R.id.importButton: // Import BIP-0032 encoded key and return to main view if successful
        {
            String encodedKey = ((EditText)findViewById(R.id.importText)).getText().toString();
            if(Bitcoin.addKey(encodedKey, ((Spinner)findViewById(R.id.derivationMethodSpinner)).getSelectedItemPosition()))
            {
                setResult(1);
                finish();
            }
            else
                Toast.makeText(this, R.string.failed_key_import, Toast.LENGTH_LONG).show();
            break;
        }
        }
    }
}
