package com.termux.app.captioning;

import android.os.Bundle;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalBuffer;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CaptioningFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class CaptioningFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment CaptioningFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static CaptioningFragment newInstance(String param1, String param2) {
        CaptioningFragment fragment = new CaptioningFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    public CaptioningFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }


    public void testCaptioning() {
        Log.w("Test Captioning", "Success!!!");
        TerminalBuffer buffer = ((TermuxActivity)getActivity()).getTerminalView().mEmulator.getScreen();
        Log.w("Preparing to  write", "Test");
        Log.w("Buffer", "" + buffer.getmLines()[buffer.externalToInternalRow(0)].getmText()[1]);
        ((TermuxActivity)getActivity()).getTerminalView().mEmulator.append(new byte[]{72},1);
        //Note: this doesn't update the screen.
        ((TermuxActivity)getActivity()).getTerminalView().viewDriver.checkAndHandle(((TermuxActivity)getActivity()).getTerminalView().getTopRow());
        ((TermuxActivity)getActivity()).getTerminalView().invalidate();
        Log.w("Write to emulator", "Test");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_captioning, container, false);

        Button button = (Button) view.findViewById(R.id.btn_test_captioning);
        button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // do something
                testCaptioning();
            }
        });

        return view;
    }
}
