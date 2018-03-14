package com.buaa.graphConnector;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import java.io.*;
import java.util.ArrayList;
import java.util.Random;

public class pathFactorModel{
    float[][]UserMatrix=null;
    ArrayList<String> Users=null;
    long n_user=0;
    String filename="";
    public pathFactorModel(String filename){
        this.filename=filename;
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

    //path search based exp losing method
    public void searchPathFactor(){
        float [][]searchMatrix=new float[(int) n_user][(int)n_user];
        ArrayList<parallelSearchTask> pools=new ArrayList<>();
        final int max_threads_num=parallelSearchTask.max_threads_num;
        for(int i=0;i<n_user;i++){
            if(pools.size()<max_threads_num) {
                parallelSearchTask p = new parallelSearchTask(UserMatrix, searchMatrix, i, (int) n_user,this);
                pools.add(p);
                p.start();
            }
            else {
                boolean tag=true;
                for(int j=0;j<pools.size();j++){
                    parallelSearchTask p=pools.get(j);
                    //System.out.println("running checking, "+p.startid+" : "+p.running);
                    if(p.running==false){
                        try {
                            p.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        p=new parallelSearchTask(UserMatrix,searchMatrix,i,(int)n_user,this);
                        pools.set(j,p);
                        p.start();
                        tag=false;
                        break;
                    }
                }
                if(tag) {
                    synchronized (this) {
                        try {
                            //System.out.println("next startid=" + i + " wait");
                            i--;
                            this.wait();

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        for(int i=0;i<pools.size();i++){
            parallelSearchTask p=pools.get(i);
            if(p.running) {
                try {
                    p.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        for(int i=0;i<n_user;i++)
            for(int j=0;j<n_user;j++){
                if(UserMatrix[i][j]!=0||i!=j)
                    UserMatrix[i][j]=searchMatrix[i][j];
            }
    }

    //exponetial decay model for a full graph building
    public void connectWithExpLose(){
        double alpha=parallelExpLoseSubtask.alpha;
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
        final int sub_task_num=parallelExpLoseSubtask.max_threads_num;
        int step=(int)this.n_user/sub_task_num;
        ArrayList<parallelExpLoseSubtask> pools=new ArrayList<>();
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
                parallelExpLoseSubtask p=new parallelExpLoseSubtask(this,k,begin,end,UserMatrix,n_user,pathL);
                p.start();
                pools.add(p);
            }
            //System.out.println("main task, begin="+sub_task_num*step+", end="+n_user);
            for(int i=sub_task_num*step;i<this.n_user;i++){
                for(int j=0;j<this.n_user;j++){
                    if(i==j) {

                        continue;
                    }
                    float curp = (float) (this.UserMatrix[i][k]* Math.exp(-alpha*(pathL[k][j]+1))
                            + this.UserMatrix[k][j]*Math.exp(-alpha*(pathL[i][k]+1)));
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
                    parallelExpLoseSubtask p=pools.get(i);
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
    public void matrix_factorization(double alpha,double beta){
        int M=this.UserMatrix.length,N=this.UserMatrix[0].length;
        int K=(int)(Math.min(M,N)*0.008);
        if(K<20)
            K=20;
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

}

