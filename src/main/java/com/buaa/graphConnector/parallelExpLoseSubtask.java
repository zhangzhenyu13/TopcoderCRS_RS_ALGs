package com.buaa.graphConnector;

public class parallelExpLoseSubtask extends Thread{
    float[][]UserMatrix;
    long n_user;
    int[][]pathL;
    final static double alpha=2.0;
    final static int max_threads_num=8;
    int k,begin,end;
    boolean running=true;
    pathFactorModel outer;
    public parallelExpLoseSubtask(pathFactorModel outer,int k,int begin,int end,float[][]UserMatrix,long n_user,int[][]pathL){
        this.outer=outer;
        this.k=k;
        this.begin=begin;
        this.end=end;
        this.UserMatrix=UserMatrix;
        this.n_user=n_user;
        this.pathL=pathL;
    }
    public void run(){
        //System.out.println("in subtask thread, begin="+begin+", end="+end);
        float curp=0;
        for(int i=begin;i<this.end;i++){
            for(int j=0;j<this.n_user;j++){
                if(i==j) {

                    continue;
                }
                curp = (float) (this.UserMatrix[i][k]* Math.exp(-this.alpha*(1+this.pathL[k][j]))
                        + this.UserMatrix[k][j]*Math.exp(-this.alpha*(1+pathL[i][k])));
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

