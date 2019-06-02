package org.SilverBlog.client;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.*;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.gson.*;
import okhttp3.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.SilverBlog.client.recycler_view_adapter.sharedpreferences;

public class post_list_activity extends AppCompatActivity {
    SwipeRefreshLayout swipe_refresh_widget;
    NavigationView navigation_view;
    private RecyclerView recycler_view;
    private Context context;
    private Toolbar toolbar;

    public static Boolean is_abs_url(String URL) {
        try {
            URI u = new URI(URL);
            return u.isAbsolute();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static String get_abs_url(String absolutePath, String relativePath) {
        try {
            URL absoluteUrl = new URL(absolutePath);
            URL parseUrl = new URL(absoluteUrl, relativePath);
            return parseUrl.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return "";
        }
    }

    void start_login() {
        Intent main_activity = new Intent(post_list_activity.this, org.SilverBlog.client.main_activity.class);
        startActivity(main_activity);
        finish();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        String host_save;
        String password_save;
        sharedpreferences = getSharedPreferences("data", MODE_PRIVATE);
        host_save = sharedpreferences.getString("host", null);
        password_save = sharedpreferences.getString("password_v2", null);
        public_value.init = true;
        if (password_save == null || host_save == null) {
            start_login();
            return;
        }
        host_save = host_save.replace("http://", "").replace("https://", "");
        public_value.host = host_save;
        public_value.password = password_save;
        setContentView(R.layout.activity_post_list_card);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Loading...");
        setSupportActionBar(toolbar);
        recycler_view = findViewById(R.id.my_recycler_view);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recycler_view.setLayoutManager(layoutManager);
        recycler_view.setHasFixedSize(true);
        recycler_view.setItemAnimator(new DefaultItemAnimator());
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        result_receiver receiver = new result_receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(context.getPackageName());
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            Intent new_post_activity = new Intent(post_list_activity.this, edit_activity.class);
            startActivity(new_post_activity);
        });


        navigation_view = findViewById(R.id.nav_view);
        swipe_refresh_widget = findViewById(R.id.swipe_refresh_widget);
        swipe_refresh_widget.setColorSchemeResources(R.color.colorPrimary);
        swipe_refresh_widget.setOnRefreshListener(() -> {
            get_post_list();
            get_menu_list();
        });

        get_post_list();
        get_menu_list();
        get_system_info();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.post_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.send_to_git_button:
                final ProgressDialog dialog = new ProgressDialog(post_list_activity.this);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setTitle(getString(R.string.loading));
                dialog.setMessage(getString(R.string.loading_message));
                dialog.setIndeterminate(false);
                dialog.setCancelable(false);
                dialog.show();
                OkHttpClient okHttpClient = public_func.get_okhttp_obj();
                Gson gson = new Gson();
                sign_json request_json_obj = new sign_json();
                request_json_obj.send_time = System.currentTimeMillis();
                request_json_obj.sign = public_func.get_hmac_hash("git_page_publish", public_value.password + request_json_obj.send_time, "HmacSHA512");
                RequestBody body = RequestBody.create(public_value.JSON, gson.toJson(request_json_obj));
                Request request = new Request.Builder().url("https://" + public_value.host + "/control/" + public_value.API_VERSION + "/git_page_publish").method("POST", body).build();
                Call call = okHttpClient.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        dialog.cancel();
                        Snackbar.make(findViewById(R.id.toolbar), R.string.git_push_error, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        dialog.cancel();
                        if (response.code() != 200) {
                            Looper.prepare();
                            Snackbar.make(findViewById(R.id.toolbar), getString(R.string.request_error) + response.code(), Snackbar.LENGTH_LONG).show();
                            Looper.loop();
                            return;
                        }
                        JsonParser parser = new JsonParser();
                        final JsonObject objects = parser.parse(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
                        int result_message = R.string.git_push_error;
                        if (objects.get("status").getAsBoolean()) {
                            result_message = R.string.submit_success;
                        }
                        Snackbar.make(findViewById(R.id.toolbar), result_message, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }
                });
                break;
            case R.id.logout:
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.remove("host");
                editor.remove("password_v2");
                editor.apply();
                start_login();

                break;
        }

        return super.onOptionsItemSelected(item);
    }

    void get_post_list() {
        swipe_refresh_widget.setRefreshing(true);
        Request request = new Request.Builder().url("https://" + public_value.host + "/control/" + public_value.API_VERSION + "/get/list/post").build();
        OkHttpClient okHttpClient = public_func.get_okhttp_obj();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Looper.prepare();
                Snackbar.make(findViewById(R.id.toolbar), R.string.network_error, Snackbar.LENGTH_LONG).show();
                Looper.loop();
            }

            @Override
            public void onResponse(Call call, final Response response) {
                if (response.code() != 200) {
                    Looper.prepare();
                    Snackbar.make(findViewById(R.id.toolbar), getString(R.string.request_error) + response.code(), Snackbar.LENGTH_LONG).show();
                    Looper.loop();
                    return;
                }
                runOnUiThread(() -> {
                    JsonParser parser = new JsonParser();
                    final List<post_list> post_list = new ArrayList<>();
                    assert response.body() != null;
                    JsonArray result_array = null;
                    try {
                        result_array = parser.parse(Objects.requireNonNull(response.body().string())).getAsJsonArray();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    public_value.post_list = result_array;

                    swipe_refresh_widget.setRefreshing(false);

                    assert result_array != null;
                    for (JsonElement item : result_array) {
                        JsonObject sub_item = item.getAsJsonObject();
                        post_list list_obj = new post_list();
                        list_obj.title = sub_item.get("title").getAsString();
                        list_obj.excerpt = sub_item.get("excerpt").getAsString();
                        list_obj.uuid = sub_item.get("uuid").getAsString();
                        post_list.add(list_obj);
                    }
                    if (result_array.size() == 0) {
                        Snackbar.make(swipe_refresh_widget, R.string.list_is_none, Snackbar.LENGTH_LONG).show();
                    }
                    recycler_view_adapter adapter = new recycler_view_adapter(post_list, post_list_activity.this);
                    recycler_view.setAdapter(adapter);
                });
            }
        });

    }

    void get_system_info() {
        Request request = new Request.Builder().url("https://" + public_value.host + "/control/system_info").build();
        OkHttpClient okHttpClient = public_func.get_okhttp_obj();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Looper.prepare();
                Snackbar.make(findViewById(R.id.toolbar), R.string.network_error, Snackbar.LENGTH_LONG).show();
                Looper.loop();

            }

            @Override
            public void onResponse(Call call, final Response response) {
                if (response.code() != 200) {
                    Looper.prepare();
                    Snackbar.make(findViewById(R.id.toolbar), getString(R.string.request_error) + response.code(), Snackbar.LENGTH_LONG).show();
                    Looper.loop();
                    return;
                }
                runOnUiThread(() -> {
                    JsonParser parser = new JsonParser();
                    JsonObject result_object = null;
                    try {
                        assert response.body() != null;
                        result_object = parser.parse(Objects.requireNonNull(response.body().string())).getAsJsonObject();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    assert result_object != null;
                    if (result_object.get("api_version").getAsInt() < public_value.current_api_code) {
                        new AlertDialog.Builder(post_list_activity.this)
                                .setMessage(getString(R.string.api_too_low))
                                .show();
                        return;
                    }
                    if (result_object.get("api_version").getAsInt() > public_value.current_api_code) {
                        new AlertDialog.Builder(post_list_activity.this)
                                .setMessage(R.string.api_too_high)
                                .show();
                        return;
                    }
                    View header_view = navigation_view.getHeaderView(0);
                    ImageView ivAvatar = header_view.findViewById(R.id.imageView);
                    String imageURL = result_object.get("author_image").getAsString();
                    if (!is_abs_url(imageURL)) {
                        imageURL = get_abs_url(public_value.host, imageURL);
                    }

                    Glide.with(post_list_activity.this).load(imageURL).apply(RequestOptions.circleCropTransform()).into(ivAvatar);
                    TextView username = header_view.findViewById(R.id.username);
                    TextView desc = header_view.findViewById(R.id.desc);
                    username.setText(result_object.get("author_name").getAsString());
                    desc.setText(result_object.get("project_description").getAsString());
                    toolbar.setTitle(result_object.get("project_name").getAsString());
                });

            }
        });

    }

    void get_menu_list() {
        Request request = new Request.Builder().url("https://" + public_value.host + "/control/" + public_value.API_VERSION + "/get/list/menu").build();
        OkHttpClient okHttpClient = public_func.get_okhttp_obj();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Looper.prepare();
                Snackbar.make(findViewById(R.id.toolbar), R.string.network_error, Snackbar.LENGTH_LONG).show();
                Looper.loop();
            }

            @Override
            public void onResponse(Call call, final Response response) {
                if (response.code() != 200) {
                    Looper.prepare();
                    Snackbar.make(findViewById(R.id.toolbar), getString(R.string.request_error) + response.code(), Snackbar.LENGTH_LONG).show();
                    Looper.loop();
                    return;
                }
                runOnUiThread(() -> {
                    JsonParser parser = new JsonParser();
                    JsonArray result_array = null;
                    assert response.body() != null;
                    try {
                        result_array = parser.parse(Objects.requireNonNull(response.body().string())).getAsJsonArray();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    public_value.menu_list = result_array;
                    navigation_view.getMenu().clear();
                    int id = 0;
                    assert result_array != null;
                    for (JsonElement item : result_array) {
                        JsonObject sub_item = item.getAsJsonObject();
                        navigation_view.getMenu().add(Menu.NONE, id, Menu.NONE, sub_item.get("title").getAsString());
                        id++;
                    }
                    navigation_view.setNavigationItemSelectedListener(item -> {
                        int id1 = item.getItemId();
                        JsonArray menu_list = public_value.menu_list;
                        JsonObject menu_item = menu_list.get(id1).getAsJsonObject();
                        if (menu_item.has("absolute")) {
                            Uri uri = Uri.parse(menu_item.get("absolute").getAsString());
                            startActivity(new Intent(Intent.ACTION_VIEW, uri));
                            return false;
                        }
                        Intent intent = new Intent(context, edit_activity.class);
                        intent.putExtra("edit", true);
                        intent.putExtra("uuid", menu_item.get("uuid").getAsString());
                        intent.putExtra("menu", true);
                        intent.putExtra("share_title", public_value.share_title);
                        intent.putExtra("share_text", public_value.share_text);
                        public_value.share_text = null;
                        public_value.share_title = null;
                        startActivity(intent);
                        DrawerLayout drawer = findViewById(R.id.drawer_layout);
                        drawer.closeDrawer(GravityCompat.START);
                        return false;
                    });
                });


            }
        });
    }

    class result_receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("result")) {
                Snackbar.make(findViewById(R.id.toolbar), intent.getStringExtra("result"), Snackbar.LENGTH_LONG).show();
            }
            if (intent.getBooleanExtra("success", false)) {
                get_post_list();
                get_menu_list();
            }
        }
    }
}



class recycler_view_adapter extends RecyclerView.Adapter<recycler_view_adapter.card_view_holder> {

    static SharedPreferences sharedpreferences;
    private List<post_list> post_list;
    private Context context;

    recycler_view_adapter(List<post_list> post_list, Context context) {
        this.post_list = post_list;
        this.context = context;
    }

    @NonNull
    @Override
    public card_view_holder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(context).inflate(R.layout.cardview, viewGroup, false);
        return new card_view_holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull card_view_holder personViewHolder, @SuppressLint("RecyclerView") final int position) {

        personViewHolder.title.setText(post_list.get(position).title);
        personViewHolder.excerpt.setText(post_list.get(position).excerpt);
        personViewHolder.card_view.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle(R.string.select).setItems(new String[]{context.getString(R.string.modify), context.getString(R.string.delete)}, (dialogInterface, i) -> {
            switch (i) {
                case 0:
                    Intent intent = new Intent(context, edit_activity.class);
                    intent.putExtra("edit", true);
                    intent.putExtra("uuid", post_list.get(position).uuid);
                    intent.putExtra("share_title", public_value.share_title);
                    intent.putExtra("share_text", public_value.share_text);
                    public_value.share_text = null;
                    public_value.share_title = null;
                    context.startActivity(intent);
                    break;
                case 1:
                    new AlertDialog.Builder(context).setTitle(R.string.notice).setMessage(R.string.delete_notify).setNeutralButton(R.string.ok_button,
                            (dialogInterface1, i1) -> {
                                final ProgressDialog dialog = new ProgressDialog(context);
                                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                                dialog.setTitle(context.getString(R.string.loading));
                                dialog.setMessage(context.getString(R.string.loading_message));
                                dialog.setIndeterminate(false);
                                dialog.setCancelable(false);
                                dialog.show();
                                JsonObject post_obj = public_value.post_list.get(position).getAsJsonObject();
                                sign_json request_json_obj = new sign_json();
                                Gson gson = new Gson();
                                request_json_obj.post_uuid = post_list.get(position).uuid;
                                request_json_obj.send_time = System.currentTimeMillis();
                                request_json_obj.sign = public_func.get_hmac_hash(request_json_obj.post_uuid + post_obj.get("title").getAsString() + post_obj.get("name").getAsString(), public_value.password + request_json_obj.send_time, "HmacSHA512");
                                RequestBody body = RequestBody.create(public_value.JSON, gson.toJson(request_json_obj));
                                Request request = new Request.Builder().url("https://" + public_value.host + "/control/" + public_value.API_VERSION + "/delete").method("POST", body).build();
                                OkHttpClient okHttpClient = public_func.get_okhttp_obj();
                                Call call = okHttpClient.newCall(request);
                                call.enqueue(new Callback() {
                                    @Override
                                    public void onFailure(Call call, IOException e) {
                                        dialog.cancel();
                                        Intent broadcast_intent = new Intent();
                                        broadcast_intent.putExtra("result", context.getString(R.string.submit_error));
                                        broadcast_intent.putExtra("success", false);
                                        broadcast_intent.setAction(context.getPackageName());
                                        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcast_intent);
                                    }

                                    @Override
                                    public void onResponse(Call call, Response response) throws IOException {
                                        dialog.cancel();
                                        Intent broadcast_intent = new Intent();
                                        broadcast_intent.setAction(context.getPackageName());
                                        if(response.code()!=200){
                                            broadcast_intent.putExtra("result", context.getString(R.string.request_error) + response.code()      );
                                            broadcast_intent.putExtra("success", false);
                                        }
                                        if(response.code()==200) {
                                            JsonParser parser = new JsonParser();
                                            final JsonObject objects = parser.parse(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
                                            String result_message = context.getString(R.string.submit_error);
                                            if (objects.get("status").getAsBoolean()) {
                                                result_message = context.getString(R.string.submit_success);
                                            }
                                            broadcast_intent.putExtra("result", result_message);
                                            broadcast_intent.putExtra("success", true);
                                        }
                                        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcast_intent);
                                    }
                                });
                            }).setNegativeButton(R.string.cancel, null).show();
                    break;
            }
        }).show());

    }

    @Override
    public int getItemCount() {
        return post_list.size();
    }
    static class card_view_holder extends RecyclerView.ViewHolder {
        CardView card_view = itemView.findViewById(R.id.card_view);
        TextView title = itemView.findViewById(R.id.title);
        TextView excerpt = itemView.findViewById(R.id.excerpt);
        card_view_holder(final View itemView) {
            super(itemView);
        }
    }
}

class sign_json {
    String post_uuid;
    String sign;
    long send_time;
}

class post_list {
    String title;
    String excerpt;
    String uuid;
}