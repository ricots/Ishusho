/*
 *    Rage Comic Maker for Android (c) Tamas Marki 2011-2013
 *	  This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.akalizakeza.apps.ishusho.comicmaker;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Toast;

import com.akalizakeza.apps.ishusho.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;

public class ComicMakerActivity extends AppCompatActivity implements
        ColorPickerDialog.OnColorChangedListener, WidthPicker.OnWidthChangedListener,
        ZoomPicker.OnZoomChangedListener {
    private ComicEditor mainView;
    private Map<CharSequence, Vector<String>> packImages = new HashMap<CharSequence, Vector<String>>();
    private CharSequence packSelected;
    private CharSequence folderSelected;
    private FontSelect fontselect = null;
    private ComicSettings settings = null;
    private MenuItem menuitem_OtherSource = null;
    private String lastSaveName = "";
    private ImageSelect imageSelector = null;
    private Intent intent = new Intent();
    private AppCompatActivity appcompatactivity;

    // private LinearLayout layout = null;
    private SharedPreferences mPrefs = null;
    private PackHandler packhandler = new PackHandler();
    private DraftManager draftManager;
    private AdapterView.OnItemClickListener setFontTypeListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                long arg3) {
            fontselect.dismiss();
            mainView.setCurrentFont(arg2);
            mainView.setDefaultBold(fontselect.isBold());
            mainView.setDefaultItalic(fontselect.isItalic());
            mainView.invalidate();
        }
    };

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("touchMode", mainView.getmTouchMode());
        outState.putInt("currentColor", mainView.getCurrentColor());
        outState.putInt("currentStrokeWidth", mainView.getCurrentStrokeWidth());
        outState.putInt("currentPanelCount", mainView.getPanelCount());
        outState.putBoolean("drawGrid", mainView.isDrawGrid());
        outState.putFloat("canvasScale", mainView.getCanvasScale());
        outState.putInt("canvasX", mainView.getmCanvasOffset().x);
        outState.putInt("canvasY", mainView.getmCanvasOffset().y);
        outState.putString("lastSaveName", lastSaveName);
        saveImagesToBundle(outState, mainView.getImageObjects(), "");
        saveLinesToBundle(outState, mainView.getPoints(), mainView.getPaints(),
                "");
        Vector<ComicState> history = mainView.getHistory();
        outState.putInt("historySize", history.size());
        for (int i = 0; i < history.size(); ++i) {
            // saveImagesToBundle(outState, history.get (i).mDrawables,
            // String.format("h%s", i));
            saveLinesToBundle(outState, history.get(i).linePoints,
                    history.get(i).mLinePaints, String.format("h%s", i));
            outState.putInt(String.format("h%spanelCount", i),
                    history.get(i).mPanelCount);
        }
    }

    private void setDetailTitle() {
        if (lastSaveName != "")
            setTitle(getString(R.string.app_name)
                    + " - "
                    + lastSaveName
                    + " - "
                    + String.format("%.0f%%", mainView.getCanvasScale() * 100.0));
        else
            setTitle(getString(R.string.app_name)
                    + " - "
                    + String.format("%.0f%%", mainView.getCanvasScale() * 100.0));

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
//        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
//                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.comic_activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mainView = (ComicEditor) findViewById(R.id.editor); /*
                                                             * new ComicEditor
															 * (this, new
															 * ComicEditor
															 * .ZoomChangeListener
															 * () { public void
															 * ZoomChanged(float
															 * newScale) {
															 * setDetailTitle
															 * (); } });
															 */
        mainView.zoomChangeListener = new ComicEditor.ZoomChangeListener() {
            public void ZoomChanged(float newScale) {
                setDetailTitle();
            }
        };
        registerForContextMenu(mainView);

        mPrefs = getSharedPreferences("RageComicMaker", 0);

        // setContentView(layout);
        packImages = packhandler.getBundles(getAssets());
        draftManager = new DraftManager(ComicMakerActivity.this, mainView,
                packhandler);
        if (savedInstanceState != null) {
            if (savedInstanceState.getSerializable("touchMode") != null)
                mainView.setmTouchMode((ComicEditor.TouchModes) savedInstanceState
                        .getSerializable("touchMode"));
            mainView.setCurrentColor(savedInstanceState.getInt("currentColor"));
            mainView.setCurrentStrokeWidth(savedInstanceState
                    .getInt("currentStrokeWidth"));
            mainView.setPanelCount(savedInstanceState
                    .getInt("currentPanelCount"));
            mainView.setDrawGrid(savedInstanceState.getBoolean("drawGrid"));
            mainView.setCanvasScale(savedInstanceState.getFloat("canvasScale"));
            mainView.setmCanvasOffset(new Point(savedInstanceState
                    .getInt("canvasX"), savedInstanceState.getInt("canvasY")));
            lastSaveName = savedInstanceState.getString("lastSaveName");
            setDetailTitle();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            if (metrics.widthPixels > mainView.getCanvasDimensions().width())
                mainView.setCanvasScale((float) metrics.widthPixels
                        / (float) mainView.getCanvasDimensions().width());
            mainView.resetHistory();
            final Bundle sis = savedInstanceState;
            draftManager.autoLoad(mainView.getStateRef());
            int hs = 0;
            if (sis != null)
                hs = sis.getInt("historySize", 0);
            for (int i = 0; i < hs; ++i) {
                ComicState cs = mainView.getStateCopy();
                // cs.mDrawables =
                // //loadImagesFromBundle(savedInstanceState,
                // String.format("h%s", i));
                cs.linePoints = loadPointsFromBundle(sis,
                        String.format("h%s", i));
                cs.mLinePaints = new LinkedList<Paint>();
                if (sis != null)
                    cs.mPanelCount = sis.getInt(String.format(
                            "h%spanelCount", i));
                for (int j = 0; j < cs.linePoints.size(); ++j) {
                    cs.mLinePaints.add(getPaintForPoint(sis, j,
                            String.format("h%s", i)));
                }
                mainView.pushHistory(cs);
            }
            mainView.resetLinesCache();
            mainView.invalidate();
        }
        for (ImageObject io : loadImagesFromBundle(savedInstanceState, "")) {
            mainView.pureAddImageObject(io);
        }
        Vector<float[]> points = loadPointsFromBundle(savedInstanceState, "");
        for (int i = 0; i < points.size(); ++i) {
            float[] p = points.get(i);
            if (p == null)
                continue;
            mainView.pureAddLine(p, getPaintForPoint(savedInstanceState, i, ""));
        }
        setOrient();
        // add some test objects
        if (false && mainView.getImageObjects().size() == 0) {
            // for (int i = 0; i < externalImages.get
            // ("default pack").get("Speech Bubbles").size(); ++i) {
            for (int i = 0; i < 2; ++i) {
                Bitmap b = packhandler.getDefaultPackDrawable("Speech Bubbles",
                        packImages.get("Speech Bubbles").get(i), 0, getAssets());
                mainView.addImageObject(b, 10 * i, 10 * i, 0.0f, 1.0f, 0,
                        "default pack", "Speech Bubbles", packImages.get("Speech Bubbles")
                                .get(i), null);
            }
            // for (int i = 0; i < externalImages.get
            // ("default pack").get("Items").size(); ++i) {
            for (int i = 0; i < 2; ++i) {
                Bitmap b = packhandler.getDefaultPackDrawable("Items",
                        packImages.get("Items").get(i), 0, getAssets());
                mainView.addImageObject(b, 20 * i, 20 * i, 0.0f, 1.0f, 0,
                        "default pack", "Items", packImages.get("Items")
                                .get(i), null);
            }
            TextObject to = new TextObject(100, 100, 50, Color.RED, 0,
                    "Yeah", false, false);
            to.setSelected(true);
            to.setInBack(false);
            mainView.pureAddImageObject(to);

        }

/*        findViewById(R.id.comicmakerMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(ComicMakerActivity.this, v);
                //Inflating the Popup using xml file
                popup.getMenuInflater().inflate(R.menu.menu_comic, popup.getMenu());
                popup.setOnMenuItemClickListener(popupListener);
                popup.show();
                //openOptionsMenu();
            }
        }); */

        mainView.invalidate();
    }

    @Override
    public void onDestroy() {
        for (ImageObject io : mainView.getImageObjects()) {
            io.recycle();
        }
        for (ComicState cs : mainView.getHistory()) {
            for (ImageObject io : cs.mDrawables) {
                try {
                    io.recycle();
                } catch (Exception e) {

                }
            }
        }
        mainView.resetHistory();
        mainView.resetObjects();
        SharedPreferences.Editor ed = mPrefs.edit();
        int c = mPrefs.getInt("runcount", 0);
        ed.putInt("runcount", c + 1);
        ed.commit();
        // packhandler.freeAllCache();
        super.onDestroy();
        // mainView = null;
        // layout = null;
    }

    private void saveImagesToBundle(Bundle outState, Vector<ImageObject> ios,
                                    String tag) {
        outState.putInt(tag + "imageObjectCount", ios.size());
        for (int i = 0; i < ios.size(); ++i) {
            int[] params = new int[2];
            params[0] = ios.get(i).getPosition().x;
            params[1] = ios.get(i).getPosition().y;
            outState.putIntArray(String.format(tag + "ImageObject%dpos", i),
                    params);
            outState.putFloat(String.format(tag + "ImageObject%drot", i), ios
                    .get(i).getRotation());
            outState.putFloat(String.format(tag + "ImageObject%dscale", i), ios
                    .get(i).getScale());
            outState.putString(String.format(tag + "ImageObject%dpack", i),
                    ios.get(i).pack);
            outState.putString(String.format(tag + "ImageObject%dfolder", i),
                    ios.get(i).folder);
            outState.putString(String.format(tag + "ImageObject%dfile", i),
                    ios.get(i).filename);
            outState.putString(String.format(tag + "ImageObject%duri", i),
                    ios.get(i).uri != null ? ios.get(i).uri.toString() : "");
            outState.putBoolean(String.format(tag + "ImageObject%dfv", i), ios
                    .get(i).isFlipVertical());
            outState.putBoolean(String.format(tag + "ImageObject%dfh", i), ios
                    .get(i).isFlipHorizontal());
            outState.putBoolean(
                    String.format(tag + "ImageObject%dselected", i), ios.get(i)
                            .isSelected());
            outState.putBoolean(String.format(tag + "ImageObject%dback", i),
                    ios.get(i).isInBack());
            outState.putBoolean(String.format(tag + "ImageObject%dlocked", i),
                    ios.get(i).locked);
            try {
                TextObject to = (TextObject) ios.get(i);
                if (to != null) {
                    outState.putInt(
                            String.format(tag + "TextObject%dtextSize", i),
                            to.getTextSize());
                    outState.putInt(
                            String.format(tag + "TextObject%dcolor", i),
                            to.getColor());
                    outState.putInt(
                            String.format(tag + "TextObject%dtypeface", i),
                            to.getTypeface());
                    outState.putString(
                            String.format(tag + "ImageObject%dtext", i),
                            to.getText());
                    outState.putBoolean(
                            String.format(tag + "TextObject%dbold", i),
                            to.isBold());
                    outState.putBoolean(
                            String.format(tag + "TextObject%ditalic", i),
                            to.isItalic());
                }
            } catch (Exception e) {
                Log.w("RAGE", e.toString());
                e.printStackTrace();
                outState.putString(String.format(tag + "ImageObject%dtext", i),
                        "");
            }
            // ios.get(i).recycle();
        }
    }

    private void saveLinesToBundle(Bundle outState, Vector<float[]> points,
                                   LinkedList<Paint> paints, String tag) {
        outState.putInt(tag + "lineCount", points.size());
        for (int i = 0; i < points.size(); ++i) {
            outState.putFloatArray(String.format(tag + "line%s", i),
                    points.get(i));
            outState.putFloat(String.format(tag + "line%dstroke", i), paints
                    .get(i).getStrokeWidth());
            outState.putInt(String.format(tag + "line%dcolor", i), paints
                    .get(i).getColor());
        }
    }

    private Vector<ImageObject> loadImagesFromBundle(Bundle savedInstanceState,
                                                     String tag) {
        Vector<ImageObject> ret = new Vector<ImageObject>();
        int ioCount = 0;
        if (savedInstanceState != null)
            ioCount = savedInstanceState.getInt(tag + "imageObjectCount", 0);
        for (int i = 0; i < ioCount; ++i) {
            int[] params = savedInstanceState.getIntArray(String.format(tag
                    + "ImageObject%dpos", i));
            float rot = savedInstanceState.getFloat(String.format(tag
                    + "ImageObject%drot", i));
            float sc = savedInstanceState.getFloat(String.format(tag
                    + "ImageObject%dscale", i));
            String text = savedInstanceState.getString(String.format(tag
                    + "ImageObject%dtext", i));
            String pack = savedInstanceState.getString(String.format(tag
                    + "ImageObject%dpack", i));
            String folder = savedInstanceState.getString(String.format(tag
                    + "ImageObject%dfolder", i));
            String file = savedInstanceState.getString(String.format(tag
                    + "ImageObject%dfile", i));
            Uri uri = null;
            if (savedInstanceState.getString(String.format(tag
                    + "ImageObject%duri", i)).length() > 0) {
                uri = Uri.parse(savedInstanceState.getString(String.format(tag
                        + "ImageObject%duri", i)));
            }

            ImageObject io = null;
            Bitmap dr = null;
            try {
                if (text.length() > 0) {
                    int ts = savedInstanceState.getInt(
                            String.format(tag + "TextObject%dtextSize", i), 20);
                    int col = savedInstanceState.getInt(
                            String.format(tag + "TextObject%dcolor", i),
                            Color.BLACK);
                    int tf = savedInstanceState.getInt(
                            String.format(tag + "TextObject%dtypeface", i), 0);
                    boolean bld = savedInstanceState.getBoolean(String.format(
                            tag + "TextObject%dbold", i));
                    boolean itlic = savedInstanceState.getBoolean(String
                            .format(tag + "TextObject%ditalic", i));
                    io = new TextObject(params[0], params[1], ts, col, tf,
                            text, bld, itlic);
                    io.setScale(sc);
                    io.setRotation(rot);
                } else if (pack.length() > 0) {
                    dr = packhandler.getDefaultPackDrawable(folder, file, 0,
                            getAssets());
                    if (dr != null) {
                        dr = dr.copy(Bitmap.Config.ARGB_8888, false);
                    }
                } else if (file.length() > 0) {
                    // BitmapFactory.Options options=new
                    // BitmapFactory.Options();
                    // options.inSampleSize = 8;
                    // dr = BitmapFactory.decodeFile(file, options);
                    dr = packhandler.decodeFile(new File(file));
                } else if (uri != null) {
                    InputStream imageStream = null;
                    try {
                        imageStream = getContentResolver().openInputStream(uri);
                        dr = BitmapFactory.decodeStream(imageStream);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
                if (dr != null) {
                    io = new ImageObject(dr, params[0], params[1], rot, sc, 0,
                            pack, folder, file);
                    io.uri = uri;
                }
            } catch (Exception e) {
                Toast.makeText(this,
                        "Comic Maker internal problem: " + e.toString(),
                        Toast.LENGTH_SHORT).show();
            }
            if (io != null) {
                io.locked = savedInstanceState.getBoolean(String.format(tag
                        + "ImageObject%dlocked", i));
                io.setSelected(savedInstanceState.getBoolean(String.format(tag
                        + "ImageObject%dselected", i)));
                io.setFlipHorizontal(savedInstanceState.getBoolean(String
                        .format(tag + "ImageObject%dfh", i)));
                io.setFlipVertical(savedInstanceState.getBoolean(String.format(
                        tag + "ImageObject%dfv", i)));
                io.setInBack(savedInstanceState.getBoolean(String.format(tag
                        + "ImageObject%dback", i)));
                ret.add(io);
            }
        }
        // packhandler.freeAllCache();
        return ret;

    }

    private Vector<float[]> loadPointsFromBundle(Bundle savedInstanceState,
                                                 String tag) {
        Vector<float[]> ret = new Vector<float[]>();
        if (savedInstanceState == null)
            return ret;
        int pc = savedInstanceState.getInt(tag + "lineCount", 0);
        for (int i = 0; i < pc; ++i) {
            float p[] = savedInstanceState.getFloatArray(String.format(tag
                    + "line%s", i));
            ret.add(p);
        }
        return ret;
    }

    private Paint getPaintForPoint(Bundle savedInstanceState, int lineInd,
                                   String tag) {
        Paint pp = new Paint();
        pp.setStrokeWidth(savedInstanceState.getFloat(String.format(tag
                + "line%dstroke", lineInd)));
        pp.setColor(savedInstanceState.getInt(String.format(
                tag + "line%dcolor", lineInd)));
        if (pp.getAlpha() == 0)
            pp.setXfermode(mainView.transparentXfer);
        return pp;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_comic, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        ActivityManager am = (ActivityManager) ComicMakerActivity.this
                .getSystemService(ACTIVITY_SERVICE);
        Log.d("ComicMaker", "Memory: " + String.valueOf(am.getMemoryClass()));
        int[] z = new int[1];
        z[0] = Process.myPid();
        Debug.MemoryInfo[] mis = am.getProcessMemoryInfo(z);
        // Print to log and read in DDMS
        // Log.i( "RAGE", " minfo.lowMemory " + mInfo.lowMemory );
        // Log.i( "RAGE", " minfo.threshold " + mInfo.threshold );
        switch (menuItem.getItemId()) {
            case R.id.about:
                AlertDialog alertDialog;
                alertDialog = new AlertDialog.Builder(ComicMakerActivity.this).create();
                alertDialog.setTitle(R.string.app_name);
                String versionname = "1";
                try {
                    PackageInfo manager = getPackageManager().getPackageInfo(
                            getPackageName(), 0);
                    versionname = manager.versionName;
                } catch (PackageManager.NameNotFoundException nof) {

                }
                alertDialog.setMessage("Ishusho v" + versionname
                        + "\n"
                        + getResources().getString(R.string.about_text));
                alertDialog.setButton(getResources().getString(R.string.home_page),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                String url = "http://yambistudios.com";
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                i.setData(Uri.parse(url));
                                startActivity(i);
                            }
                        });
                alertDialog.setIcon(R.mipmap.ic_launcher);
                alertDialog.show();
                break;
            case (R.id.pen_color):
            case (R.id.text_color):
                ColorPickerDialog cpd = new ColorPickerDialog(ComicMakerActivity.this, ComicMakerActivity.this, "key",
                        ComicMakerActivity.this.mainView.getCurrentColor(), ComicMakerActivity.this.mainView.getCurrentColor());
                cpd.show();
                break;
            case (R.id.pen_width):
                WidthPicker np = new WidthPicker(ComicMakerActivity.this, ComicMakerActivity.this,
                        ComicMakerActivity.this.mainView.getCurrentStrokeWidth());
                np.show();
                break;
            case (R.id.zoom):
                ZoomPicker zp = new ZoomPicker(ComicMakerActivity.this, ComicMakerActivity.this,
                        ComicMakerActivity.this.mainView.getCanvasScale());
                zp.show();
                break;
            case (R.id.drafts):
                draftManager.show();
                draftManager
                        .setOnDismissListener(new DialogInterface.OnDismissListener() {
                            public void onDismiss(DialogInterface dialog) {
                                mainView.resetLinesCache();
                                mainView.invalidate();
                            }
                        });
                break;
            case (R.id.clear):
                AlertDialog alertDialog2;
                alertDialog2 = new AlertDialog.Builder(ComicMakerActivity.this).create();
                alertDialog2.setTitle(R.string.confirmation);
                alertDialog2.setMessage(getResources().getString(
                        R.string.clear_question));
                alertDialog2.setButton(getResources().getString(R.string.action_yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                lastSaveName = "";
                                mainView.resetObjects();
                                mainView.invalidate();
                            }
                        });
                alertDialog2.setButton2(getResources().getString(R.string.action_no),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                alertDialog2.show();
                break;

            case (R.id.text_type):
                fontselect = new FontSelect(ComicMakerActivity.this, setFontTypeListener,
                        mainView.getDefaultFontSize(), mainView.isDefaultBold(),
                        mainView.isDefaultItalic());
                fontselect.show();
                break;
            case (R.id.settings):
                settings = new ComicSettings(ComicMakerActivity.this, mainView.getPanelCount(),
                        mainView.isDrawGrid(), mPrefs.getInt(
                        "orient", 0), mPrefs.getString("format", "JPG"),
                        new View.OnClickListener() {

                            public void onClick(View v) {
                                mainView.setPanelCount(settings.getPanelCount());
                                mainView.setDrawGrid(settings.getDrawGrid());
                                SharedPreferences.Editor ed = mPrefs.edit();
                                ed.putInt("orient", settings.getOrientation());
                                ed.putString("format", settings.getSaveFormat());
                                ed.commit();
                                setOrient();
                                settings.dismiss();
                                mainView.invalidate();
                            }
                        });
                settings.show();
                break;
            case (R.id.share):
                if (lastSaveName == "") {
                    AlertDialog.Builder alert = new AlertDialog.Builder(ComicMakerActivity.this);
                    alert.setTitle(R.string.select_name);
                    final EditText input = new EditText(ComicMakerActivity.this);
                    alert.setView(input);
                    alert.setPositiveButton(getResources().getString(R.string.action_ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                    doSave(input.getText().toString(), true);
                                }
                            });

                    alert.setNegativeButton(
                            getResources().getString(R.string.action_cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int whichButton) {
                                }
                            });

                    alert.show();
                } else {
                    doSave(lastSaveName, true);
                }
                break;
            case (R.id.save):
                AlertDialog.Builder salert = new AlertDialog.Builder(ComicMakerActivity.this);

                salert.setTitle(R.string.select_name);
                // Set an EditText view to get user input
                final EditText sinput = new EditText(ComicMakerActivity.this);
                sinput.setText(lastSaveName);
                salert.setView(sinput);

                salert.setPositiveButton(getResources().getString(R.string.action_ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                doSave(sinput.getText().toString(), false);
                            }
                        });

                salert.setNegativeButton(getResources().getString(R.string.action_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                            }
                        });

                salert.show();
                break;
            case (R.id.exit):
                draftManager.saveDraft(mainView.getStateRef(), "auto", true);
                finish();
                System.runFinalization();
                System.exit(2);
                break;
            case (R.id.redo):
                mainView.unpopState();
                break;
            case R.id.add_pack:
                packSelected = getResources().getString(R.string.built_in);
                doComicPackFolderSelect();
                break;
            case R.id.add_other:
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(
                        Intent.createChooser(
                                intent,
                                getResources().getString(
                                        R.string.select_picture)), 1);
                break;
        }
        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        ImageObject io = mainView.getSelected();
        if (menu.size() == 0 && io != null) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_comic_edit, menu);
            menu.findItem(R.id.tofront).setVisible(io.isInBack());
            menu.findItem(R.id.toback).setVisible(!io.isInBack());
            TextObject to = null;
            try {
                to = (TextObject) io;
            } catch (Exception e) {

            }
            menu.findItem(R.id.edit).setVisible(to != null);
            menu.findItem(R.id.color).setVisible(to != null);
            mainView.resetClick();
        }
    }

//    public boolean onMenuItemClick(){
//    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ImageObject io = mainView.getSelected();
        if (item.getItemId() == R.id.toback && io != null)
            io.setInBack(true);
        else if (item.getItemId() == R.id.tofront && io != null)
            io.setInBack(false);
        else if (item.getItemId() == R.id.remove && io != null) {
            mainView.removeImageObject(io);
        } else if (item.getItemId() == R.id.flipH && io != null) {
            io.setFlipHorizontal(!io.isFlipHorizontal());
        } else if (item.getItemId() == R.id.flipV && io != null) {
            io.setFlipVertical(!io.isFlipVertical());
        } else if (item.getItemId() == R.id.lock && io != null) {
            io.locked = !io.locked;
        } else if (item.getItemId() == R.id.edit && io != null) {
            try {
                final TextObject to = (TextObject) io;
                AlertDialog.Builder salert = new AlertDialog.Builder(this);

                salert.setTitle(R.string.enter_text);
                // Set an EditText view to get user input
                final EditText sinput = new EditText(this);
                sinput.setText(to.getText());
                salert.setView(sinput);

                salert.setPositiveButton(getResources().getString(R.string.action_ok),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                to.setText(sinput.getText().toString());
                                mainView.invalidate();
                            }
                        });

                salert.setNegativeButton(
                        getResources().getString(R.string.action_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                            }
                        });

                salert.show();
            } catch (Exception e) {

            }
        } else if (item.getItemId() == R.id.color && io != null) {
            try {
                final TextObject to = (TextObject) io;
                ColorPickerDialog.OnColorChangedListener ocl = new ColorPickerDialog.OnColorChangedListener() {
                    public void colorChanged(String key, int color) {
                        to.setColor(color);
                        to.regenerateBitmap();
                        mainView.invalidate();
                    }
                };
                ColorPickerDialog cpd = new ColorPickerDialog(this, ocl, "key",
                        to.getColor(), to.getColor());
                cpd.show();
            } catch (Exception e) {

            }
        }
        mainView.invalidate();
        return super.onContextItemSelected(item);
    }

    private void setOrient() {
        int i = mPrefs.getInt("orient", 0);
        switch (i) {
            case 0:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                break;
            case 1:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case 2:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
        }
    }

    @SuppressLint("NewApi")
    private void doSave(String fname, boolean doShare) {
        CharSequence text = getResources().getString(R.string.comic_saved_as)
                + " ";
        try {
            String ReservedChars = "|\\?*<\":>+[]/'";
            for (char c : ReservedChars.toCharArray()) {
                fname = fname.replace(c, '_');
            }
            String value = fname;
            Bitmap b = mainView.getSaveBitmap();
            if (b == null) {
                text = getResources().getString(R.string.comic_save_fail_1);
                ;
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
                return;
            }
            File folder = getFilesDir();
            if (externalStorageAvailable()) {
                try {
                    folder = Environment
                            .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    if (!folder.exists() || !folder.canWrite()) {
                        folder = Environment
                                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    }
                    if (!folder.exists() || !folder.canWrite()) {
                        folder = Environment.getExternalStorageDirectory();
                    }
                } catch (Exception e) {
                    folder = Environment.getExternalStorageDirectory();
                } catch (Error e) {
                    folder = Environment.getExternalStorageDirectory();
                }
                if (!folder.exists() || !folder.canWrite()) {
                    folder = getFilesDir();
                }
            }
            /*
             * String folder =
			 * Environment.getExternalStorageDirectory().toString() +
			 * "/Pictures"; try { folder =
			 * Environment.getExternalStoragePublicDirectory
			 * (Environment.DIRECTORY_PICTURES).toString(); } catch
			 * (NoSuchFieldError e) {
			 *
			 * }
			 */
            String ext = ".jpg";
            if (mPrefs.getString("format", "JPG").equals("PNG"))
                ext = ".png";
            String fullname = folder.getAbsolutePath() + File.separator + value
                    + ext;
            FileOutputStream fos;
            if (folder == getFilesDir())
                fos = openFileOutput(value + ext, Context.MODE_WORLD_WRITEABLE);
            else {
                File f2 = new File(fullname);// openFileOutput(fname,
                // Context.MODE_PRIVATE);//new
                // FileOutputStream(fullname);
                fos = new FileOutputStream(f2);
            }
            if (ext.equals(".png"))
                b.compress(Bitmap.CompressFormat.PNG, 95, fos);
            else
                b.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.close();
            String[] str = new String[1];
            str[0] = fullname;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
                MediaScannerConnection.scanFile(this, str, null, null);
            }
            text = text + value + ext + " "
                    + getResources().getString(R.string.saved_end);
            ;
            lastSaveName = value;
            setDetailTitle();
            if (doShare) {
                Intent share = new Intent(Intent.ACTION_SEND);
                if (ext.equals(".png"))
                    share.setType("image/png");
                else
                    share.setType("image/jpeg");

                share.putExtra(Intent.EXTRA_STREAM,
                        Uri.parse("file://" + fullname.replace(" ", "%20")));
                share.putExtra(Intent.EXTRA_TITLE, value);

                startActivity(Intent.createChooser(share, getResources()
                        .getString(R.string.share)));
            }
        } catch (Exception e) {
            Map<String, String> hm = new HashMap<String, String>();
            hm.put("text", e.toString());
            //EasyTracker.getInstance(this).send(MapBuilder.createEvent("ui_action", "save", "exception", null).build());
            e.printStackTrace();
            text = getResources().getString(R.string.comic_save_fail_2)
                    + e.toString();
        } catch (Error e) {
            Map<String, String> hm = new HashMap<String, String>();
            hm.put("text", e.toString());
            //EasyTracker.getInstance(this).send(MapBuilder.createEvent("ui_action", "save", "error", null).build());
            e.printStackTrace();
            text = getResources().getString(R.string.comic_save_fail_2)
                    + e.toString();
        }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private boolean externalStorageAvailable() {
        boolean mExternalStorageAvailable;
        boolean mExternalStorageWriteable;
        String state = Environment.getExternalStorageState();

        if (state.equals(Environment.MEDIA_MOUNTED)) {
            // We can read and write the media
            mExternalStorageAvailable = mExternalStorageWriteable = true;
        } else if (state.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            // We can only read the media
            mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        } else {
            // Something else is wrong. It may be one of many other states, but
            // all we need
            // to know is we can neither read nor write
            mExternalStorageAvailable = mExternalStorageWriteable = false;
        }
        return mExternalStorageAvailable && mExternalStorageWriteable;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean success = false;

        if (resultCode == RESULT_OK) {

            if (requestCode == 1) {
                String fname = data.getData().toString();
                if (fname == null) {
                } else if (fname.startsWith("content://")) {
                    //fname = getRealPathFromURI(data.getData());
                    Uri selectedImage = data.getData();
                    grantUriPermission(getPackageName(), selectedImage, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    if (Build.VERSION.SDK_INT >= 19) {
                        //getContentResolver().takePersistableUriPermission(selectedImage, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 8;
                    // Bitmap b = BitmapFactory.decodeFile(fname, options);
                    InputStream imageStream = null;
                    try {
                        imageStream = getContentResolver().openInputStream(selectedImage);
                        Bitmap b = BitmapFactory.decodeStream(imageStream);

                        if (b != null) {
                            mainView.addImageObject(b,
                                    -mainView.getmCanvasOffset().x,
                                    -mainView.getmCanvasOffset().y, 0.0f, 1.0f, 0,
                                    "", "", "", selectedImage);
                            success = true;
                            mainView.setmTouchMode(ComicEditor.TouchModes.HAND);
                        }
                    } catch (FileNotFoundException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } else if (fname != null && !fname.equals("")) {
                    if (fname.startsWith("file://"))
                        fname = fname.replace("file://", "");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 8;
                    // Bitmap b = BitmapFactory.decodeFile(fname, options);
                    Bitmap b = packhandler.decodeFile(new File(fname));
                    if (b != null) {
                        mainView.addImageObject(b,
                                -mainView.getmCanvasOffset().x,
                                -mainView.getmCanvasOffset().y, 0.0f, 1.0f, 0,
                                "", "", fname, null);
                        success = true;
                        mainView.setmTouchMode(ComicEditor.TouchModes.HAND);
                    }
                }
            }
            if (!success) {
                Toast.makeText(this, R.string.error_adding_image,
                        Toast.LENGTH_LONG).show();

            }
        }
    }

    // And to convert the image URI to the direct file system path of the image
    // file
    public String getRealPathFromURI(Uri contentUri) {
        String ret = "";
        if (contentUri == null)
            return ret;

        // can post image
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            Cursor cursor = managedQuery(contentUri, proj, // Which columns to
                    // return
                    null, // WHERE clause; which rows to return (all rows)
                    null, // WHERE clause selection arguments (none)
                    null); // Order-by clause (ascending by name)
            if (cursor != null) {
                int column_index = cursor
                        .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                ret = cursor.getString(column_index);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    private void doComicPackFolderSelect() {
        CharSequence[] ccs = (CharSequence[]) packImages.keySet().toArray(
                new CharSequence[packImages.keySet().size()]);
        Arrays.sort(ccs);
        AlertDialog alertDialog;
        alertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.select_folder)
                .setItems(ccs, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which2) {

						/* User clicked so do some stuff */
                        CharSequence[] ccs = (CharSequence[]) packImages
                                .keySet().toArray(
                                        new CharSequence[packImages.keySet()
                                                .size()]);
                        Arrays.sort(ccs);
                        folderSelected = ccs[which2];
                        doComicPackImageSelect();
                    }
                }).create();
        alertDialog.show();
    }

    private void doComicPackImageSelect() {
        imageSelector = new ImageSelect(this, folderSelected, packImages,
                new ImageSelect.BackPressedListener() {

                    public void backPressed() {
                        doComicPackFolderSelect();
                        // packhandler.freeCache(packSelected, folderSelected);
                    }
                }, packhandler);
        imageSelector.clickListener = new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {
                String fname = imageSelector.myStuff[arg2];// packImages.get(folderSelected).get
                // (arg2).toString();
                Bitmap id = packhandler.getDefaultPackDrawable(
                        folderSelected.toString(), fname, 0, getAssets());
                boolean rec = true;
                Bitmap.Config conf = null;
                if (id != null) {
                    conf = id.getConfig();
                    rec = id.isRecycled();
                }
                if (conf == null)
                    conf = Bitmap.Config.ARGB_8888;
                if (id != null && conf != null && !rec) {
                    mainView.addImageObject(id.copy(conf, false),
                            -mainView.getmCanvasOffset().x,
                            -mainView.getmCanvasOffset().y, 0.0f, 1.0f, 0,
                            packSelected.toString(), folderSelected.toString(),
                            fname, null);
                    mainView.setmTouchMode(ComicEditor.TouchModes.HAND);
                } else {
                    Toast.makeText(getApplicationContext(),
                            R.string.error_adding_image, Toast.LENGTH_LONG)
                            .show();
                }
                imageSelector.dismiss();
            }
        };
        imageSelector.show();
    }

    public void colorChanged(int c) {
        mainView.setCurrentColor(c);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (!mainView.popState()) {
                AlertDialog alertDialog;
                alertDialog = new AlertDialog.Builder(this).create();
                alertDialog.setTitle(R.string.confirmation);
                alertDialog.setMessage(getResources().getString(
                        R.string.confirm_exit));
                alertDialog.setButton(getResources().getString(R.string.action_yes),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                SharedPreferences.Editor ed = mPrefs.edit();
                                int c = mPrefs.getInt("runcount", 0);
                                ed.putInt("runcount", c + 1);
                                ed.commit();
                                draftManager.saveDraft(mainView.getStateRef(),
                                        "auto", true);
                                finish();
                                System.runFinalization();
                                System.exit(2);
                            }
                        });
                alertDialog.setButton3(getResources().getString(R.string.action_no),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {

                            }
                        });
                alertDialog.setButton2(getResources()
                        .getString(R.string.drafts), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        draftManager.show();
                        draftManager
                                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                    public void onDismiss(DialogInterface dialog) {
                                        mainView.resetLinesCache();
                                        mainView.invalidate();
                                    }
                                });

                    }
                });
                alertDialog.show();
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.pen_color).setVisible(
                mainView.getmTouchMode() == ComicEditor.TouchModes.PENCIL
                        || mainView.getmTouchMode() == ComicEditor.TouchModes.LINE);
        menu.findItem(R.id.pen_width).setVisible(
                mainView.getmTouchMode() == ComicEditor.TouchModes.PENCIL
                        || mainView.getmTouchMode() == ComicEditor.TouchModes.LINE
                        || mainView.getmTouchMode() == ComicEditor.TouchModes.ERASER);
        menu.findItem(R.id.text_color).setVisible(
                mainView.getmTouchMode() == ComicEditor.TouchModes.TEXT);
        menu.findItem(R.id.text_type).setVisible(
                mainView.getmTouchMode() == ComicEditor.TouchModes.TEXT);
        menu.findItem(R.id.redo).setVisible(mainView.isRedoAvailable());

        return super.onPrepareOptionsMenu(menu);
    }

    public void widthChanged(int width) {
        mainView.setCurrentStrokeWidth(width);

    }

    public void colorChanged(String key, int color) {

        mainView.setCurrentColor(color);
    }

    private boolean handleKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
            mainView.moveEvent(-1, 0);
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
            mainView.moveEvent(1, 0);
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
            mainView.moveEvent(0, -1);
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
            mainView.moveEvent(0, 1);
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_A) {
            mainView.rotateEvent((float) ComicEditor.ROTATION_STEP);
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_D) {
            mainView.rotateEvent(-(float) ComicEditor.ROTATION_STEP);
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_W) {
            mainView.scaleEvent((float) ComicEditor.ZOOM_STEP);
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_S) {
            mainView.scaleEvent(-(float) ComicEditor.ZOOM_STEP);
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_R) {
            mainView.unpopState();
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_SPACE) {
            ImageObject io = mainView.getSelected();
            if (io != null)
                io.setInBack(!io.isInBack());
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
            ImageObject io = mainView.getSelected();
            if (io != null) {
                mainView.removeImageObject(io);
                mainView.invalidate();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleKeyEvent(event))
            return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        if (handleKeyEvent(event))
            return true;
        return super.onKeyMultiple(keyCode, repeatCount, event);
    }

    public void zoomChanged(float zoom) {
        mainView.setCanvasScale(zoom);
        mainView.invalidate();
        setDetailTitle();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int w = mainView.getWidth();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        draftManager.saveDraft(mainView.getStateRef(), "auto", true);
        super.onStop();
    }
}
