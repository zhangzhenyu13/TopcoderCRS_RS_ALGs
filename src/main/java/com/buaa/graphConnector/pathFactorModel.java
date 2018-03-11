package com.buaa.graphConnector;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import java.io.*;
import java.util.ArrayList;
import java.util.Random;

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
        long t0 = System.currentTimeMillis();
        JSONObject obj=null;
        JSONArray m=null;
        JSONParser parser=new JSONParser();
        try {
            BufferedReader bfr=new BufferedReader(new FileReader("data/initGraph/"+filename));
            //System.out.println("ok1");
            obj=(JSONObject) parser.parse(bfr);
            //System.out.println("ok2");
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
        long t1 = System.currentTimeMillis();

        System.out.println("loaded data from "+filename+",time cost "+(t1-t0)+"ms ,size="+n_user+", sparse "+countZero());
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
        long t0 = System.currentTimeMillis();
        int [][]pathL=new int[(int) n_user][(int) n_user];

        for(int i=0;i<pathL.length;i++)
            for(int j=0;j<pathL[0].length;j++) {
                if (UserMatrix[i][j] != 0 || i == j)
                    pathL[i][j] = 0;
                else
                    pathL[i][j]=1000000;
            }
        long t1 = System.currentTimeMillis();
        System.out.println("pathLen init finished in "+(t1-t0)+"ms");
        final int sub_task_num=16;
        int step=(int)this.n_user/sub_task_num;
        ArrayList<parallelSUbtask> pools=new ArrayList<>();
        int begin,end;
        if(n_user<2000)
            step=0;
        for(int k=0;k<n_user;k++){
            if((k+1)%100==0){
                t1=System.currentTimeMillis();
                System.out.println("k/n:"+k+"/"+n_user+", time cost="+(t1-t0)+"ms");
                t0=System.currentTimeMillis();
            }
            for(int i=0;i<sub_task_num*step;i+=step){
                begin=i;
                end=begin+step;
                parallelSUbtask p=new parallelSUbtask(this,k,begin,end,UserMatrix,n_user,pathL,alpha);
                p.start();
                pools.add(p);
            }
            //System.out.println("main task, begin="+sub_task_num*step+", end="+n_user);
            for(int i=sub_task_num*step;i<this.n_user;i++){
                for(int j=0;j<this.n_user;j++){
                    if(i==j) {

                        continue;
                    }
                    float curp = (float) (this.UserMatrix[i][k]* Math.exp(-alpha*(pathL[i][k]+pathL[k][j]+1))
                            * this.UserMatrix[k][j]);
                    if(Math.abs(this.UserMatrix[i][j]) < Math.abs(curp)) {
                        this.UserMatrix[i][j] = curp;
                        pathL[i][j] = pathL[i][k]+pathL[k][j]+1;
                    }
                }
            }

            boolean tag=true;
            while(tag){
                //System.out.println("sub task status checking");
                tag=false;
                for(int i=0;i<pools.size();i++){
                    parallelSUbtask p=pools.get(i);
                    if(p.running==false) {
                        try {
                            p.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        //System.out.println("task end="+p.end+": "+p.running);
                        tag=true;
                        break;
                    }

                }
                if(tag) {
                    synchronized (this) {
                        try {
                            //System.out.println("waiting for wake up");
                            this.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }

    }
    //matrix factorization model for a full graph building
    public void matrix_factorization(int K,int M, int N,double alpha,double beta){
        System.out.println("para:"+K+","+M+","+N);
        float [][]P=new float[M][K];
        float [][]Q=new float[K][N];
        float e,eij,Rij=0,prevE=1000;
        float [][]eR=new float[M][N];
        long steps= (long) 1e+8,convCount=0;
        Random random=new Random();
        random.setSeed(1203433);
        for(int i=0;i<M;i++)
            for(int k=0;k<K;k++) {
                P[i][k] = random.nextFloat();
                //System.out.println(P[i][k]);
            }
        for(int k=0;k<K;k++)
            for(int j=0;j<N;j++) {
                Q[k][j] = random.nextFloat();
                //System.out.println(Q[k][j]);

            }
        long t0 = System.currentTimeMillis();

        for(long step=0;step<steps;step++) {
            for(int i=0;i<M;i++) {
                for (int j = 0; j < N; j++) {
                    Rij=0;
                    if(UserMatrix[i][j] != 0) {
                        for(int k=0;k<K;k++)
                            Rij+=P[i][k]*Q[k][j];
                        eij = UserMatrix[i][j] - Rij;
                        //System.out.println("P,Q,"+eij);
                        for(int k=0;k<K;k++) {
                            P[i][k] =(float) (P[i][k] + alpha * (2 * eij * Q[k][j] - beta * P[i][k]));
                            Q[k][j] =(float) (Q[k][j] + alpha * (2 * eij * P[i][k] - beta * Q[k][j]));
                            //System.out.println(P[i][k]+": "+Q[k][j]);
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
                        //System.out.println(eR[i][j]);
                        e += Math.pow(UserMatrix[i][j] - eR[i][j], 2);
                        for(int k=0;k<K;k++)
                            e +=  (beta/2) * (Math.pow(P[i][k],2) + Math.pow(Q[k][j],2));
                    }
                }
            }

            if((step + 1)%100 == 0){
                long t1=System.currentTimeMillis();
                System.out.println("MF "+filename+": step="+(step+1)+" ,e="+e+" ,time="+(t1-t0)+"ms");
                t0 = System.currentTimeMillis();
            }
            //exist judge
            if(e==prevE){
                convCount+=1;
                //System.out.println(convCount+", pe="+prevE);
            }
            else {
                convCount=0;
            }
            prevE=e;
            if(e< 0.1||convCount>10)
                break;
        }
        for(int i=0;i<M;i++){
            for(int j=0;j<N;j++){
                if(i!=j)
                    UserMatrix[i][j]=eR[i][j];

            }
        }
    }
    //thread run
    public void run(){
        System.out.println("thread running using "+filename);

        long t0=System.currentTimeMillis();
        this.loadData();
        String savePath="";

        if(mode==1) {
            this.connectWithExpLose(0.1);
            savePath="data/fullGraph/full";
        }
        else if(mode==2){
            this.matrix_factorization((int)(0.3*n_user),(int) n_user,(int) n_user,0.0002,0.02);
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
        new ThreadsPool(1,0,20).start();
    }
}

class ThreadsPool extends Thread{
    int max_threads_num;
    int begin_cluster_no;
    int clusers_num;
    public ThreadsPool(int max_size,int begin,int clusters_num){
        this.max_threads_num=max_size;
        this.begin_cluster_no=begin;
        this.clusers_num=clusters_num;
    }
    synchronized public void run(){
        final int mode=1,choice=1;
        ArrayList<pathFactorModel> pool_threads=new ArrayList<>();
        for(int i=begin_cluster_no;i<clusers_num;i++) {
            if(pool_threads.size()<max_threads_num){
                System.out.println("adding thread to pools");
                pathFactorModel pfm=new pathFactorModel(choice,i,mode,this);
                pfm.start();
                pool_threads.add(pfm);
            }
            else{
                boolean tag=false;
                for(int j=0;j<pool_threads.size();j++){

                    pathFactorModel p=pool_threads.get(j);
                    System.out.println(p.filename+" running:"+p.running+",checking");

                    if(p.running==false) {
                        System.out.println(p.filename+" fin,start new thread");
                        tag=true;
                        try {
                            p.join();
                            p=null;
                            System.gc();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        p=new pathFactorModel(choice,i,mode,this);
                        pool_threads.set(j,p);
                        p.start();
                        break;
                    }
                }
                if(tag==false){
                    i--;
                    System.out.println("pool manager wait,next #:"+(i+1));

                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("pool manager wake,next #:"+(i+1));
                }
            }

        }

        for(pathFactorModel p:pool_threads) {
            if(p.running==false) {
                try {
                    p.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}

class parallelSUbtask extends Thread{
    float[][]UserMatrix;
    long n_user;
    int[][]pathL;
    double alpha;
    int k,begin,end;
    boolean running=true;
    pathFactorModel outer;
    public parallelSUbtask(pathFactorModel outer,int k,int begin,int end,float[][]UserMatrix,long n_user,int[][]pathL,double alpha){
        this.outer=outer;
        this.k=k;
        this.begin=begin;
        this.end=end;
        this.UserMatrix=UserMatrix;
        this.n_user=n_user;
        this.pathL=pathL;
        this.alpha=alpha;
    }
    public void run(){
        //System.out.println("in subtask thread, begin="+begin+", end="+end);
        float curp=0;
        for(int i=begin;i<this.end;i++){
            for(int j=0;j<this.n_user;j++){
                if(i==j) {

                    continue;
                }
                curp = (float) (this.UserMatrix[i][this.k]* Math.exp(-this.alpha*(1+this.pathL[i][this.k]+this.pathL[this.k][j]))
                        * this.UserMatrix[this.k][j]);
                if(Math.abs(this.UserMatrix[i][j]) < Math.abs(curp)) {
                    this.UserMatrix[i][j] = curp;
                    this.pathL[i][j] = this.pathL[i][this.k]+this.pathL[this.k][j]+1;
                    //if(curp>100)
                    //    System.out.println(curp+", "+k+", "+pathL[i][k]+", "+pathL[k][j]+", "+UserMatrix[i][k]+", "+UserMatrix[k][j]);
                }
            }
        }
        synchronized (this.outer){
            this.running=false;
            //System.out.println("sub task end:"+this.end);
            this.outer.notify();

        }
    }
}