package com.buaa.graphConnector;

import java.util.ArrayList;

public class ThreadsPool extends Thread{
    int max_threads_num;
    public ThreadsPool(int max_size){
        this.max_threads_num=max_size;
    }
    synchronized public void run(){
        final int mode=1,choice=1;
        ArrayList<pathFactorModel> pool_threads=new ArrayList<>();
        for(int i=0;i<30;i++) {
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
            try {
                p.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}

