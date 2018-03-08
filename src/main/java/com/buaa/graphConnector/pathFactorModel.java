package com.buaa.graphConnector;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import java.io.*;
import java.util.ArrayList;

public class pathFactorModel extends Thread{
    float[][]UserMatrix=null;
    ArrayList<String> Users=null;
    long n_user=0;
    String filename="";
    Boolean running=true;
    int mode=0;
    Thread externalSig=null;
    public pathFactorModel(int choice,int k_no,int mode,Thread ext){
        this.mode=mode;
        filename="initG_"+choice+"_"+k_no+".json";
        running=true;
        this.externalSig=ext;
    }

    public void loadData(){

        JSONObject obj=null;
        JSONArray m=null;
        JSONParser parser=new JSONParser();
        try {
            BufferedReader bfr=new BufferedReader(new FileReader("data/initGraph/"+filename));
            obj=(JSONObject) parser.parse(bfr);
            m=(JSONArray) obj.get("data");
            n_user=(long)obj.get("size");
            Users=(ArrayList<String>)obj.get("users");
            bfr.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        UserMatrix=new float[(int)n_user][(int)n_user];

        for(int i=0;i<n_user;i++){

            JSONArray vec= (JSONArray) m.get(i);
            for(int j=0;j<n_user;j++){
                if(i==j){
                    UserMatrix[i][j]=0;
                    continue;
                }
                UserMatrix[i][j]=Float.parseFloat(vec.get(j).toString());

            }
        }
        System.out.println("loaded data from "+filename+",size="+n_user+", sparse "+countZero());

    }
    public int countZero(){
        int count=0;
        for(int i=0;i<n_user;i++)
            for(int j=0;j<n_user;j++)
                if(UserMatrix[i][j]!=0.0) {
                    count++;
                    //System.out.println(UserMatrix[i][j]);
                }
        return count;
    }
    //exponetial decay model for a full graph building
    public void connectWithExpLose(double alpha){
        int [][]pathL=new int[(int) n_user][(int) n_user];
        for(int i=0;i<pathL.length;i++)
            for(int j=0;j<pathL[0].length;j++) {
                if (UserMatrix[i][j] != 0 || i == j)
                    pathL[i][j] = 0;
                else
                    pathL[i][j]=1000000;
            }

        float curp=0;
        for(int k=0;k<n_user;k++){
            for(int i=0;i<n_user;i++){
                for(int j=0;j<n_user;j++){
                    if(i==j) {

                        continue;
                    }
                    curp = (float) (UserMatrix[i][k]* Math.exp(-alpha*(pathL[i][k]+pathL[k][j])) * UserMatrix[k][j]);
                    if(Math.abs(UserMatrix[i][j]) < Math.abs(curp)) {
                        UserMatrix[i][j] = curp;
                        pathL[i][j] = pathL[i][k]+pathL[k][j]+1;
                        //if(curp>100)
                        //    System.out.println(curp+", "+k+", "+pathL[i][k]+", "+pathL[k][j]+", "+UserMatrix[i][k]+", "+UserMatrix[k][j]);
                    }
                }
            }
        }
    }
    //matrix factorization model for a full graph building
    public void matrix_factorization(int K,int M, int N,double alpha,double beta){
        float [][]P=new float[M][K];
        float [][]Q=new float[K][N];
        float e,eij,Rij=0;
        float [][]eR=new float[M][N];
        long steps=10000;
        for(int i=0;i<M;i++)
            for(int k=0;k<K;k++)
                P[i][k]=(float)0.0;
        for(int k=0;k<K;k++)
            for(int j=0;j<N;j++)
                Q[k][j]=(float)0.0;
        for(long step=0;step<steps;step++) {
            long t0 = System.currentTimeMillis();
            for(int i=0;i<M;i++) {
                for (int j = 0; j < N; j++) {
                    if(UserMatrix[i][j] != 0.0) {
                        for(int k=0;k<K;k++)
                            Rij+=P[i][k]*Q[k][j];
                        eij = UserMatrix[i][j] - Rij;
                        for(int k=0;k<K;k++) {
                            P[i][k] =(float) (P[i][k] + alpha * (2 * eij * Q[k][j] - beta * P[i][k]));
                            Q[k][j] =(float) (Q[k][j] + alpha * (2 * eij * P[i][k] - beta * Q[k][j]));
                        }
                    }

                }
            }
            e=0;
            for(int i=0;i<M;i++){
                for(int j=0;j<N;j++){
                    eR[i][j]=0;
                    for(int k=0;k<K;k++)
                        eR[i][j]+=P[i][k]*Q[k][j];
                    if (UserMatrix[i][j] != 0) {
                        e += Math.pow(UserMatrix[i][j] - eR[i][j], 2);
                        for(int k=0;k<K;k++)
                            e +=  (beta/2) * (Math.pow(P[i][k],2) + Math.pow(Q[k][j],2));
                    }
                }
            }

            if((step + 1)%1 == 0){
                long t1=System.currentTimeMillis();
                System.out.println("filling "+filename+": step="+(step+1)+" ,e="+e+" ,time="+(t1-t0)+"ms");
            }
            if(e< 0.001)
                break;
        }
        for(int i=0;i<M;i++){
            for(int j=0;j<N;j++){
                UserMatrix[i][j]=eR[i][j];

            }
        }
    }
    public void run(){
        System.out.println("thread running using "+filename);

        long t0=System.currentTimeMillis();
        this.loadData();
        String savePath="";

        if(mode==1) {
            this.connectWithExpLose(0.6);
            savePath="data/fullGraph/full";
        }
        else if(mode==2){
            this.matrix_factorization((int)(0.6*n_user),(int) n_user,(int) n_user,0.6,0.8);
            savePath="data/rebuildGraph/rebuild";

        }
        JSONObject obj=new JSONObject();
        obj.put("n_users",n_user);
        obj.put("users",Users);
        ArrayList<ArrayList<Float>> data=new ArrayList<>();
        for(int i=0;i<n_user;i++){
            ArrayList<Float> vec=new ArrayList<>();
            for(int j=0;j<n_user;j++)
                vec.add(j,UserMatrix[i][j]);
            data.add(i,vec);
        }
        obj.put("data",data);
        try {
            BufferedWriter bfw=new BufferedWriter(new FileWriter(savePath+filename.substring(4)));
            obj.writeJSONString(bfw);
            bfw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        long t1=System.currentTimeMillis();
        synchronized (externalSig) {
            running=false;
            System.out.println(filename+",fin sparse "+countZero()+",time cost "+(t1-t0)+"ms");
            externalSig.notify();
        }
    }
    public static void main(String[] args)
    {
        new ThreadsPool(1).start();
    }
}

