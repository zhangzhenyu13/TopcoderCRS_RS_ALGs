package com.buaa.graphConnector;

import java.util.ArrayList;

public class parallelSearchTask extends Thread{
    float[][] cost_m;
    float[][] matrix_settings;
    final int startid;
    int targetid_num;
    final static double alpha=1.0;
    final static int max_threads_num=8;
    pathFactorModel outerSig;
    boolean running=true;
    class Node{
        int id;
        ArrayList<Float> cost=new ArrayList<>();

        Node prevNode=null;
        public Node(int id){
            this.id=id;

        }
        public Node connectNode(int nextid,float cost){
            Node node=new Node(nextid);
            for(int i=0;i<this.cost.size();i++)
                node.cost.add(i,this.cost.get(i));
            node.cost.add(this.cost.size(),cost);
            node.prevNode=this;
            return node;
        }
        public ArrayList<Node> getNeighbors(){
            int n=cost_m.length;
            ArrayList<Node> nbs=new ArrayList<>();
            for(int i=0;i<n;i++){
                if(id==i||cost_m[id][i]==0)
                    continue;
                Node nb=this.connectNode(i,cost_m[id][i]);
                nbs.add(nb);
            }
            return nbs;
        }
        public boolean sameNode(Node node){
            return this.id==node.id;
        }
        public float getValue(){
            float value=0;
            int L=this.cost.size();
            for(int i=0;i<this.cost.size();i++){
                value+=this.cost.get(i)*Math.exp(-alpha*(L-i));
            }
            return value;
        }
        public void showPath(){
            String s="";
            int L=cost.size();
            s+=L;
            for(int i=0;i<cost.size();i++)
                s+=":("+cost.get(i)+","+cost.get(i)*Math.exp(-alpha*(L-i))+")";
            System.out.println(s);
        }
    }
    public parallelSearchTask(float[][] cost_m,float[][]matrix_settings,int startid,int targetid_num,pathFactorModel outerSig){
        this.cost_m=cost_m;
        this.matrix_settings=matrix_settings;
        this.startid=startid;
        this.targetid_num=targetid_num;
        this.outerSig=outerSig;
    }
    public void run(){
        System.out.println("starid "+startid+" is running "+this.running+", search size="+targetid_num);
        for(int targetid=0;targetid<targetid_num;targetid++) {
            //System.out.println("searching from "+startid+" to "+targetid);
            if(targetid==startid||cost_m[startid][targetid]!=0)
                continue;

            ArrayList<Node> openT = new ArrayList<>(), closeT = new ArrayList<>();
            Node cur = new Node(startid);
            Node target = new Node(targetid);
            openT.add(cur);
            int pos;

            while (openT.size() > 0) {
                cur = openT.get(0);
                openT.remove(0);
                closeT.add(cur);
                if (cur.sameNode(target)) {
                    target = cur;
                    System.out.println("found target " + target.id+" from start "+startid+" value="+target.getValue());
                    //target.showPath();
                    break;
                }
                ArrayList<Node> nbs = cur.getNeighbors();
                for (Node node : nbs) {
                    //for each node in neighbors
                    boolean tag=false;//in closeT ?
                    for (int i = 0; i < closeT.size(); i++) {
                        if (node.sameNode(closeT.get(i))) {
                            tag=true;
                            break;
                        }
                    }
                    if(tag)
                        continue;
                    //in openT ?
                    for(int i=0;i<openT.size();i++){
                        if(node.sameNode(openT.get(i))){
                            tag=true;
                            if(openT.get(i).getValue()<node.getValue()){
                                openT.remove(i);
                                tag=false;
                            }
                            break;
                        }
                    }
                    if(tag)
                        continue;
                    //find pos to insert node
                    for (pos = 0; pos < openT.size(); pos++) {
                        if (Math.abs(openT.get(pos).getValue()) < Math.abs(node.getValue()))
                            break;
                    }
                    openT.add(pos, node);
                }
                //System.out.println("from "+startid+" to "+targetid+", openT size="+openT.size()+", closeT size="+closeT.size());
            }

            this.matrix_settings[startid][targetid] = target.getValue();
        }
        synchronized (outerSig){
            this.running=false;
            //System.out.println("startID "+startid+" finished searching");
            this.outerSig.notify();
        }
    }
}
