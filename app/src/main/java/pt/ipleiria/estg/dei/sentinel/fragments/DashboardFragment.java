package pt.ipleiria.estg.dei.sentinel.fragments;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;



import java.util.ArrayList;
import java.util.List;

import pt.ipleiria.estg.dei.sentinel.Constants;
import pt.ipleiria.estg.dei.sentinel.activities.MainActivity;
import pt.ipleiria.estg.dei.sentinel.R;


public class DashboardFragment extends Fragment {

    //-----------------UI----------------
    private TextView qoa;
    private TextView humidade;
    private TextView temperatura;
    private TextView ultimaData;
    private ProgressBar pb;
    private ProgressBar pbTemp;
    private ProgressBar pbHum;
    private Spinner spinnerRooms;
    private ImageButton btnShare;

    //------------variables---------------
    private DatabaseReference mDatabase;
    public static final String TAG = "Dashboard";
    private DataSnapshot ds;
    //para depois usar como query para encontrar
    private String selected = "Edificio A";
    private int selectedIndex = 0;
    private float temperaturasSum = 0;
    private float humidadeSum = 0;
    private int numTemperatura = 0;
    private int numHumidade = 0;
    private float mediaHum = 0;
    private float mediaTemp = 0;
    private String hora = "";
    private int checkListener = 0;
    private ArrayAdapter<String> adapter;
    private String data = "";






    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        getActivity().setTitle("DASHBOARD");


        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    ds = dataSnapshot;
                    updateUIOnDataChange(dataSnapshot);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read value.", e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w(TAG, "Failed to read value.", databaseError.toException());
            }
        });

    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View view = inflater.inflate(R.layout.fragment_dashboard,container,false);

        qoa = view.findViewById(R.id.textViewQOA);
        humidade = view.findViewById(R.id.textViewHumidade);
        temperatura = view.findViewById(R.id.textViewTemperatura);
        ultimaData = view.findViewById(R.id.textViewData);
        pb = (ProgressBar) view.findViewById(R.id.progressBar2);
        spinnerRooms = view.findViewById(R.id.spinnerRooms);
        pbTemp = view.findViewById(R.id.progressBarTemperatura);
        pbHum = view.findViewById(R.id.progressBarHumidade);
        btnShare = view.findViewById(R.id.btnShare);


        /*CALLS MAIN ACTIVITY METHOD TO TWEET*/
        view.findViewById(R.id.btnShare).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).loginToTwitter();
            }
        });


        //check if user is authenticated
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // User is  not signed in
            //nao consegue alterar o sitio
            spinnerRooms.setEnabled(false);
            spinnerRooms.setClickable(false);
            spinnerRooms.setAlpha(0.5f);
            btnShare.setVisibility(View.INVISIBLE);

        }else{
            spinnerRooms.setAlpha(1);
        }

        //user is signed in
        spinnerRooms.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    //este if é para o listener nao dar trigger quando o spinner é criado
                    if (++checkListener > 1) {
                        selectedIndex = position;
                        updateUIOnItemSelected();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read value.", e);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        return view;
    }


    public void updateUIOnDataChange(DataSnapshot dataSnapshot) {
        //ir buscar a ultima data de selected
        data = getlatestDate(dataSnapshot, selected);
        //lista de rooms
        List<String> roomsList = new ArrayList<>();
        //adicionar "Edificio A" hardcoded ja que nao está na bd
        roomsList.add("Edificio A");

        //iterar pelos nodes de "rooms" todos e addicionar a lista
        for (DataSnapshot rooms : dataSnapshot.getChildren()) {
            roomsList.add(rooms.getKey());
        }
        setSpinnerData(roomsList);


        //dar setup ao adapter e atribuilo ao spinner ( para meter os rooms todos sempre na dropdown)
        adapter = new ArrayAdapter<String>(this.getActivity(), R.layout.spinner_list, roomsList);
        adapter.setDropDownViewResource(R.layout.spinner_list_dropdown);
        spinnerRooms.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        //aqui como ao dar setup ao adapter atribui o indice 0 como default fazer esta verificaçao para
        //voltar ao selected value
        if (spinnerRooms.getSelectedItemPosition() != selectedIndex) {
            spinnerRooms.setSelection(selectedIndex);
        }

        //default vais buscar "EdificioA"
        //se o index estiver 0, ou seja na primeira call, ou quando "edificio A" selected, nos outros updates skippa isto pq vai fazer o listener do spinner
        if (selectedIndex == 0) {
            iterateDatasnapshot();

            //calcular medias
            mediaTemp = (temperaturasSum / numTemperatura);
            mediaHum = (humidadeSum / numHumidade);

            //limitar humidade de 0%-100%
            if (mediaHum < 0) {
                mediaHum = 0;
            }
            if (mediaHum > 100) {
                mediaHum = 100;
            }

            //atribuir medias e datas a UI
            temperatura.setText(  String.format("%.02f", mediaTemp)+ "ºC");
            humidade.setText( String.format("%.02f", mediaHum) + "%");
            ultimaData.setText("Last Update: " + data + " " + hora);



            //setup das cores segundo os nossos limites
            updateUIColors(mediaTemp, mediaHum);

            setData(temperatura.getText().toString(),humidade.getText().toString(),selected,qoa.getText().toString());

        }
    }

    public String getlatestDate(DataSnapshot dataSnapshot, String selected) {
        String data = "";
        if (selected.equals("Edificio A")) {
            for (DataSnapshot rooms : dataSnapshot.getChildren()) {
                for (DataSnapshot datas : rooms.getChildren()) {
                    if (datas.getKey().compareTo(data) > 0) {
                        data = datas.getKey();
                    }
                }
            }
        } else {
            for (DataSnapshot datas : dataSnapshot.child(selected).getChildren()) {
                if (datas.getKey().compareTo(data) > 0) {
                    data = datas.getKey();
                }
            }

        }
        return data;
    }

    public void updateUIColors(float mediaTemp, float mediaHum) {
        if (mediaTemp >= 19 && mediaTemp <= 35) {
            //verde
            //imageTemp.setBackgroundColor(Color.parseColor("#008000"));
            pbTemp.getProgressDrawable().setColorFilter(Color.parseColor("#90EE90"), PorterDuff.Mode.SRC_IN);
        } else {
            //vermelho
            //imageTemp.setBackgroundColor(Color.parseColor("#FF0000"));
            pbTemp.getProgressDrawable().setColorFilter(Color.parseColor("#8E1600"), PorterDuff.Mode.SRC_IN);

        }

        if (mediaHum >= 50 && mediaHum <= 75) {
            //verde
            //imageHum.setBackgroundColor(Color.parseColor("#008000"));
            pbHum.getProgressDrawable().setColorFilter(Color.parseColor("#90EE90"), PorterDuff.Mode.SRC_IN);

        } else {
            //vermelho
            //imageHum.setBackgroundColor(Color.parseColor("#FF0000"));
            pbHum.getProgressDrawable().setColorFilter(Color.parseColor("#8E1600"), PorterDuff.Mode.SRC_IN);

        }


        if ((mediaTemp >= 19 && mediaTemp <= 35) && (mediaHum >= 50 && mediaHum <= 75)) {
            //roda a verde
            qoa.setText("Good");
            //vai para 20%
            ObjectAnimator animation = ObjectAnimator.ofInt(pb, "progress", pb.getProgress(), 20);
            animation.setDuration(500);
            animation.setInterpolator(new DecelerateInterpolator());
            animation.start();
        } else if (((mediaTemp < 19 || mediaTemp >= 35) && (mediaHum >= 50 && mediaHum <= 75)) || (
                (mediaTemp >= 19 && mediaTemp <= 35) && (mediaHum < 50 || mediaHum >= 75))) {
            //roda a amarelo
            qoa.setText("Average");
            //vai para 50%
            ObjectAnimator animation = ObjectAnimator.ofInt(pb, "progress", pb.getProgress(), 50);
            animation.setDuration(500);
            animation.setInterpolator(new DecelerateInterpolator());
            animation.start();
        } else {
            //roda a vermelho
            qoa.setText("Bad");
            //vai para 99%
            ObjectAnimator animation = ObjectAnimator.ofInt(pb, "progress", pb.getProgress(), 99);
            animation.setDuration(500);
            animation.setInterpolator(new DecelerateInterpolator());
            animation.start();
        }
    }

    public void updateUIOnItemSelected() {
        //resetar todas as variaveis
        temperaturasSum = 0;
        humidadeSum = 0;
        numTemperatura = 0;
        numHumidade = 0;
        mediaHum = 0;
        mediaTemp = 0;
        hora = "";
        //ir buscar o selected item para string
        selected = spinnerRooms.getSelectedItem().toString();
        //ir buscar ultima data do item selecionado
        data = getlatestDate(ds, selected);

        iterateDatasnapshot();

        //calculo de medias
        mediaTemp = (temperaturasSum / numTemperatura);
        mediaHum = (humidadeSum / numHumidade);

        //limitar humidade de 0%-100%
        if (mediaHum < 0) {
            mediaHum = 0;
        }
        if (mediaHum > 100) {
            mediaHum = 100;
        }

        //atribuir a UI
        temperatura.setText( String.format("%.02f", mediaTemp) + "ºC");
        humidade.setText( String.format("%.02f", mediaHum) + "%");
        ultimaData.setText("Last Update: " + data + " " + hora);



        //update das cores com os nossos limites
        updateUIColors(mediaTemp, mediaHum);

        setData(temperatura.getText().toString(),humidade.getText().toString(),selected,qoa.getText().toString());


    }

    public void iterateDatasnapshot(){
        //se for "Edificio A" fazer a media geral de todos os rooms naquele dia
        if (selected.equals("Edificio A") || selectedIndex == 0) {
            //iterar por todos os rooms
            for (DataSnapshot rooms : ds.getChildren()) {
                for (DataSnapshot latestDate : rooms.child(data).getChildren()) {
                    for (DataSnapshot key : latestDate.getChildren()) {
                        if (key.getKey().equals("temperatura")) {
                            temperaturasSum += Float.parseFloat(key.getValue().toString());
                            numTemperatura++;
                        }
                        if (key.getKey().equals("humidade")) {
                            humidadeSum += Float.parseFloat(key.getValue().toString());
                            numHumidade++;
                        }
                        if (key.getKey().equals("hora")) {
                            if (key.getValue().toString().compareTo(hora) > 0) {
                                hora = key.getValue().toString();
                            }
                        }
                    }
                }
            }
        } else {//se nao for "Edificio A" fazer media so do room "selected" na data
            //ou seja iterar apenas pelo filho "selected"
            for (DataSnapshot latestDate : ds.child(selected).child(data).getChildren()) {
                for (DataSnapshot key : latestDate.getChildren()) {
                    if (key.getKey().equals("temperatura")) {
                        temperaturasSum += Float.parseFloat(key.getValue().toString());
                        numTemperatura++;
                    }
                    if (key.getKey().equals("humidade")) {
                        humidadeSum += Float.parseFloat(key.getValue().toString());
                        numHumidade++;
                    }
                    if (key.getKey().equals("hora")) {
                        if (key.getValue().toString().compareTo(hora) > 0) {
                            hora = key.getValue().toString();
                        }
                    }
                }
            }
        }
    }

    public void setData(String temperature,String humidity,String location,String airQuality) {
        Activity activity = getActivity();
        if(activity instanceof MainActivity) {
            ((MainActivity) activity).setData(temperature,humidity,location,airQuality);
        }
    }


    public void setSpinnerData(List<String> roomsList ) {
        Activity activity = getActivity();
        if(activity instanceof MainActivity) {
            ((MainActivity) activity).setSpinnerData(roomsList);
        }
    }



}