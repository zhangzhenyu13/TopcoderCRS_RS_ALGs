package com.buaa.graphConnector;

import org.json.simple.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class MultiRun extends Thread{
    pathFactorModel model_p;
    int mode,choice;
    String filename;
    public MultiRun(String filename,int mode,int choice){
        this.filename=filename;
        this.model_p=new pathFactorModel(filename);
        this.mode=mode;
        this.choice=choice;
    }
    public void run(){
        System.out.println("Running filling method for "+model_p.filename+", with mode="+mode+" and choice="+choice);

        long t0=System.currentTimeMillis();
        model_p.loadData();
        String savePath="";

        if(mode==1) {
            model_p.connectWithExpLose();
            savePath="data/expLoseGraph/expL";
        }
        else if(mode==2){
            model_p.matrix_factorization(0.0002,0.02);
            savePath="data/rebuildGraph/rebuild";
        }
        else if(mode==3){
            model_p.searchPathFactor();
            savePath="data/searchGraph/search";
        }
        JSONObject obj=new JSONObject();
        obj.put("n_users",model_p.n_user);
        obj.put("users",model_p.Users);
        ArrayList<ArrayList<Float>> data=new ArrayList<>();
        for(int i=0;i<model_p.n_user;i++){
            ArrayList<Float> vec=new ArrayList<>();
            for(int j=0;j<model_p.n_user;j++)
                vec.add(j,model_p.UserMatrix[i][j]);
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
        System.out.println(filename+",fin sparse "+model_p.countZero()+",time cost "+(t1-t0)+"ms");

    }
    //thread run
    public static void main(String[] args){
        final int mode=3,choice=1;
        for(int k=0;k<20;k++) {
            String filename="initG_"+choice+"_"+k+".json";
            MultiRun runInstance=new MultiRun(filename,mode,choice);
            runInstance.start();
            try {
                runInstance.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
