package org.lindev.androkom;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.Touch;
import android.text.style.ClickableSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.ViewSwitcher;

/**
 * Show texts in a LysKOM conference.
 * 
 * @author henrik
 *
 */
public class Conference extends Activity implements ViewSwitcher.ViewFactory, OnTouchListener
{

    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    /**
     * Set up activity. Will show individual LysKOM texts
     * with a click anywhere on the display moving to the 
     * next unread text. 
     */
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.conference);

        mSwitcher = (TextSwitcher)findViewById(R.id.flipper);
        mSwitcher.setFactory(this);

        mSlideLeftIn = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
        mSlideLeftOut = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
        mSlideRightIn = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
        mSlideRightOut = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);

        final Object data = getLastNonConfigurationInstance();
        final int confNo = (Integer) getIntent().getExtras().get("conference-id");

        Log.i("androkom", "Got passed conference id: " + confNo);


        if (data != null) {      	
            mState = (State)data;
            mSwitcher.setText(formatText(mState.currentText.elementAt(mState.currentTextIndex)));
        } else {    
            mState = new State();
            mState.currentText = new Stack<String>();
            mState.currentTextIndex = 0;
            getApp().getKom().setConference(confNo);
            new LoadMessageTask().execute();
            
            mSwitcher.setText(formatText("Loading text.."));
        }

        mGestureDetector = new GestureDetector(new MyGestureDetector());
       

    }
    
    /**
     * Fetch new texts asynchronously, and show a progress spinner
     * while the user is waiting.
     * 
     * @author henrik
     *
     */
    private class LoadMessageTask extends AsyncTask<Void, Integer, String> 
    {
        private final ProgressDialog dialog = new ProgressDialog(Conference.this);

        protected void onPreExecute() 
        {
            this.dialog.setCancelable(true);
            this.dialog.setIndeterminate(true);
            this.dialog.setMessage("Loading...");
            this.dialog.show();
        }

        // worker thread (separate from UI thread)
        protected String doInBackground(final Void... args) 
        {
            return ((App)getApplication()).getKom().getNextUnreadText();               
        }

        protected void onPostExecute(final String text) 
        {
            mState.currentText.push(text);                   
            mSwitcher.setText(formatText(mState.currentText.elementAt(mState.currentTextIndex)));
            
            this.dialog.dismiss();
        }
    }


    /**
     * Class for handling internal text number links. 
     * Only a skeleton for now. 
     * 
     * @author henrik
     *
     */
    class KomInternalURLSpan extends ClickableSpan {  
        String mLinkText;
        
        public KomInternalURLSpan(String mLinkText) {  
            
        }  

        @Override  
        public void onClick(View widget) {  
            // TODO Conference.this.onKomLinkClicked(mLinkText);
        }  
    }  
    
 
    /**
     *  Applies a regex to a Spannable turning the matches into
     *  links. To be used with the class above.
     */ 
    public final boolean addLinks(Spannable s, Pattern p, String scheme) {
        boolean hasMatches = false;
        Matcher m = p.matcher(s);

        while (m.find()) {
            int start = m.start();
            int end = m.end();

            String url = m.group(0);

            KomInternalURLSpan span = this.new KomInternalURLSpan(url);
            s.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
   
            hasMatches = true;           
        }

        return hasMatches;
    }

    /**
     * A gesture detector that is used to navigate within and between texts.
     * 
     * @author henrik
     *
     */
    class MyGestureDetector extends SimpleOnGestureListener 
    {        
        
        @Override
        public boolean onScroll (MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
        {
            //Log.i("androkom","got scroll event "+distanceX + " " + distanceY);
            if (Math.abs(e1.getX() - e2.getX()) > SWIPE_MAX_OFF_PATH)
                return false;

            TextView widget = (TextView)mSwitcher.getCurrentView();

            // Constrain to top of text widget.
            int newX = Math.max((int)(widget.getScrollX()+distanceX),0);
            int newY = Math.max((int)(widget.getScrollY()+distanceY),0);

            // TODO: Implement momentum scrolling.
            Touch.scrollTo(widget, widget.getLayout(), newX,  newY);

            return true;       	
        }
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) 
        {
            try {
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
                    return false;
                
                // right to left swipe
                if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    Log.i("androkom","moving to next text cur:" + mState.currentTextIndex + "/" + mState.currentText.size()); 
                    mState.currentTextIndex++;
                    
                    if (mState.currentTextIndex >= mState.currentText.size()) {
                        // At end of list. load new text from server
                        Log.i("androkom", "fetching new text");
                        new LoadMessageTask().execute();

                        mSwitcher.setInAnimation(mSlideLeftIn);
                        mSwitcher.setOutAnimation(mSlideLeftOut);
                        mSwitcher.setText("Loading text..");                     
                    }
                    else {
                        // Display old text, already fetched.
                        mSwitcher.setInAnimation(mSlideLeftIn);
                        mSwitcher.setOutAnimation(mSlideLeftOut);

                        mSwitcher.setText(formatText(mState.currentText.elementAt(mState.currentTextIndex)));
                    }
                    
                  
                } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    Log.i("androkom","moving to prev text, cur: " + (mState.currentTextIndex-1) + "/" + mState.currentText.size());
                    
                    mState.currentTextIndex--;        
                    
                    if (mState.currentTextIndex < 0) {
                        mState.currentTextIndex = 0;
                       
                    }
                    
                    mSwitcher.setInAnimation(mSlideRightIn);
                    mSwitcher.setOutAnimation(mSlideRightOut);
                    mSwitcher.setText(formatText(mState.currentText.elementAt(mState.currentTextIndex)));
                   
                }
            } catch (Exception e) {
                // nothing
            }
            return false;
        }
    }

    /**
     * This one is called when we, ourselves, have been touched.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) 
    {       
        if (mGestureDetector.onTouchEvent(event))
            return true;
        else
            return false;
    }

    /**
     * This one is called when our child TextView has been touched.
     */
    public boolean onTouch(View v, MotionEvent event) 
    {
        return onTouchEvent(event);
    }

    /**
     * When we're being temporarily destroyed, due to, for example 
     * the user rotating the screen, save our state so we can restore
     * it again.
     */
    @Override
    public Object onRetainNonConfigurationInstance() 
    {    	
        return mState;
    }


    /**
     * Called when user has selected a menu item from the 
     * menu button popup. 
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
        // Handle item selection
        switch (item.getItemId()) {

        /*
         * A reply to the current text was requested, so show a 
         * CreateText activity. 
         */
        case R.id.reply:
            Intent intent = new Intent(this, CreateText.class);    
            intent.putExtra("in-reply-to", getApp().getKom().getLastTextNo());
            startActivity(intent);
            return true;

        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * The menu key has been pressed, instantiate the requested
     * menu.
     */
    @Override 
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.conference, menu);
        return true;
    }


    public static Spannable formatText(String text)
    {
        SpannableStringBuilder spannedText = (SpannableStringBuilder)Html.fromHtml(text);       
        Linkify.addLinks(spannedText, Linkify.ALL);
        
        return spannedText;
    }
    /**
     * Return TextViews for switcher.
     */
    public View makeView() {
        TextView t = new TextView(this);
        t.setText("[no text loaded]", TextView.BufferType.SPANNABLE);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setGravity(Gravity.TOP | Gravity.LEFT);
        t.setTextColor(ColorStateList.valueOf(Color.WHITE));
        t.setOnTouchListener(this);
        return t;
    }

    App getApp() 
    {
        return (App)getApplication();
    }

    private class State {
        int currentTextIndex;
        Stack<String> currentText;        
    };
    
    State mState;

    // For gestures and animations

    private GestureDetector mGestureDetector;
    View.OnTouchListener mGestureListener;
    private Animation mSlideLeftIn;
    private Animation mSlideLeftOut;
    private Animation mSlideRightIn;
    private Animation mSlideRightOut;
    private TextSwitcher mSwitcher;



}
