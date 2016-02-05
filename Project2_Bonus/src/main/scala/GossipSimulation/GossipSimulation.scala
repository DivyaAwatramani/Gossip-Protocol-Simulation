package GossipSimulation

import java.lang.Object

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global._
import scala.concurrent.duration._
import scala.math._
import scala.util.Random

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala

sealed trait Gossip
case class IntializeNeighbours(neighbour:ArrayBuffer[ActorRef]) extends Gossip
case class InitializeSW(s: Int ,w: Int )  extends Gossip
case class PUSH(s: Double,w: Double)  extends Gossip
case class RemoveActor(callingActor: ActorRef) extends Gossip
case object complete extends Gossip
case object StartGossip extends Gossip
case object Completed extends Gossip
case object GossipReached extends Gossip
case object KillNode extends Gossip
case object selfTrigger extends Gossip


object GossipSimulation {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("GossipSimulation")
    var msg : String = ""
    val MainActor = system.actorOf(Props(new MainActor(args(0).toInt,args(1),args(2))), name = "MainActor") 
    //val MainActor = system.actorOf(Props(new MainActor(100,"full","Gossip")), name = "MainActor")
   	if(args(2) == "Gossip")
   	MainActor ! "STARTGossip" 	// Message send to the master actor for starting the gossip Algorithm
   	 else
    	MainActor ! "STARTPUSH"		// Message send to the master actor for starting the Push SUM Algorithm
  }
}


class MainActor(node: Int,topology :String ,protocol: String) extends Actor 
{
  
  import context._
  var Actorlist = new ArrayBuffer[ActorRef]() 
  var TerminatedActorCount: Int = 0
  var GossipReceiverCount: Int=0
  var nodes: Int = node
  var StartTime : Long =0
  var FinishTime : Long =0
  
  def full(){
    for (i<-0 until nodes)
      Actorlist(i) ! IntializeNeighbours(Actorlist - Actorlist(i))	// All the nodes except itself will be the neighbour of every node
  }
  
  def line(){
    var tempNeighborList= new ArrayBuffer[ActorRef]()
    tempNeighborList+=Actorlist(1)
 
    Actorlist(0) !IntializeNeighbours(tempNeighborList)    // set neighbour for the first actor
    tempNeighborList= new ArrayBuffer[ActorRef]()
    tempNeighborList += Actorlist(nodes-2)			// set neighbour for the last actor
    Actorlist(nodes-1) !IntializeNeighbours(tempNeighborList)
    for(i<- 1 until nodes-1)
    {
      tempNeighborList= new ArrayBuffer[ActorRef]()   //set the neighbours for the remaining actors:each node will have two neighbours 
      tempNeighborList +=Actorlist(i-1)
      tempNeighborList += Actorlist(i+1)
      Actorlist(i) ! IntializeNeighbours(tempNeighborList)
    }
  }

  def ThreeDPoint() : (Array[Int], Array[Int], Array[Int], Int) ={
    val c:Int = Math.floor(Math.cbrt(nodes)).toInt
    var x = new Array[Int](nodes)
    var y = new Array[Int](nodes)
    var z = new Array[Int](nodes)
    for (i<-0 until (c*c*c)){
      x(i) = -1;
      y(i) = -1;
      z(i) = -1;
    }
    x(0) = 0;    y(0) = 0;    z(0) = 0;
    for (i<-1 until c*c*c){
      x(i) = x(i-1)
      y(i) = y(i-1)
      z(i)=z(i-1)+1
      if(z(i)== c){
        z(i)=0
        y(i)=y(i)+1
        if(y(i)== c){
          y(i)=0
          x(i)=x(i)+1
        }
      }
    }    
    return (x,y,z,c)
  }
  
  def ThreeDNeighbour(x:Int, y:Int, z:Int, c:Int) : ArrayBuffer[Int] = {
      var nodeList= new ArrayBuffer[Int]()
      if(x != 0){
        var k = ((x-1)*c*c) + (y*c) + z
        nodeList += k
      }
      if(y != 0){
        var k = (x*c*c) + ((y-1)*c) + z
        nodeList += k
      }
      if(z != 0){
        var k = (x*c*c) + (y*c) + z-1
        nodeList += k
      }

      if(x != c-1){
        var k = ((x+1)*c*c) + (y*c) + z
        if(k < nodes)
          nodeList += k
      }
      if(y != c-1){
        var k = (x*c*c) + ((y+1)*c) + z
        if(k < nodes)
          nodeList += k
      }
      if(z != c-1){
        var k = (x*c*c) + (y*c) + z+1
        if(k < nodes)
          nodeList += k
      }
      return nodeList
  }

  def ThreeD()
  {
    var tempNeighborList= new ArrayBuffer[ActorRef]()
    var nodeList= new ArrayBuffer[Int]()
    var (x,y,z,c) = ThreeDPoint()
    for (i<-0 until nodes){
      tempNeighborList = new ArrayBuffer[ActorRef]()
      nodeList = ThreeDNeighbour(x(i),y(i),z(i),c)
      for (j<-0 until nodeList.length)
        tempNeighborList += Actorlist(nodeList(j))
      Actorlist(i) ! IntializeNeighbours(tempNeighborList)
    }
  }
  
  def ImpThreeD() {
    var tempNeighborList= new ArrayBuffer[ActorRef]()
    var nodeList1= new ArrayBuffer[Int]()
    var nodeList2= new ArrayBuffer[Int]()
    var (x,y,z,c) = ThreeDPoint()
    
    for (i<-0 until nodes){
      tempNeighborList = new ArrayBuffer[ActorRef]()
      nodeList2= new ArrayBuffer[Int]()
      nodeList1 = ThreeDNeighbour(x(i),y(i),z(i),c)
      for (i<-0 until nodes)
        nodeList2 +=i
      for (j<-0 until nodeList1.length) {
        tempNeighborList += Actorlist(nodeList1(j))
        nodeList2 -= nodeList1(j)
      }
      var r = Random.nextInt(nodeList2.length)
      tempNeighborList += Actorlist(nodeList2(r))
      Actorlist(i) ! IntializeNeighbours(tempNeighborList)
    }    
  }
  
  def topology_func(topology:String){	
    if(topology == "full")
      full()
    else if(topology == "3D")
      ThreeD()
    else if(topology == "line")
      line()
    else if(topology == "Imp3D")
      ImpThreeD()
  }
    
  if(topology == "3D" || topology == "Imp3D")
  {
    var t:Int = Math.floor(Math.cbrt(node)).toInt
    nodes = t*t*t
  }
  
  for (i <-0 until nodes)
    Actorlist += context.actorOf(Props(new GossipActor(this.self)),name = "GossipActor"+ i);  //create actors for the number of nodes entered
    
  topology_func(topology)
  
 
  if(protocol=="PushSum")
  {
  for (i<-0 until nodes)
    Actorlist(i) ! InitializeSW(i,1)		//Intialize the S and Weight Value 
  }
  
  def receive = {
    case "STARTPUSH" =>
      println("node number"+ nodes)
      var i= Random.nextInt(nodes)
       StartTime = System.currentTimeMillis()
          //println(" -----start time------"+ StartTime)
          
          Actorlist(i) ! PUSH(0,0)

    case Completed  => 		

      if(protocol=="PushSum")		// system will converge here as one of the node has reached the non changing s/w ratio
            {
                  FinishTime= System.currentTimeMillis()
            //     println("Finish time"+ FinishTime)
                  println("Time taken in PUSH SUM distribution using "+ topology+ "is"+ (FinishTime - StartTime))
                  context.system.shutdown()
            }
            if(protocol=="Gossip")
            {
                
                
                TerminatedActorCount+=1			// Track the number of terminated nodes
                if(TerminatedActorCount== nodes)
                {
              //    println("gossip completed")
                  FinishTime= System.currentTimeMillis()
                  println("Time taken in gossip distribution using "+ topology+ "is"+ (FinishTime - StartTime))
                  context.system.shutdown()
                }
               
            }
            
    case GossipReached =>
      GossipReceiverCount += 1
      if(GossipReceiverCount==nodes)			// if all the nodes have received the gossip atleast once then also system converges
      {
         FinishTime= System.currentTimeMillis()
        println("----All the nodes have received the gossip in "+(FinishTime - StartTime)+"atleast once hence shutting down the system---")
        context.system.shutdown()
      }
      
    case "STARTGossip" =>
      var i= Random.nextInt(nodes)  				//Actor selected randomly for initiating the gossip
        StartTime = System.currentTimeMillis()
          //println(" -----start time------"+ StartTime)
          Actorlist(i) ! StartGossip
	 for (i<-0 untill nodes/5)				//Nodes killed by master or a scheduled basis
   	 context.system.scheduler.scheduleOnce(20 milliseconds, Actorlist(Random.nextInt(nodes)), KillNode)

    
      
  }

}

class GossipActor(master: ActorRef) extends Actor
{
  import context._
  var Sindex :Double =1
  var Wvalue : Double=1
  var latestRatio:Double=0
  var previousRatio:Double=0
  var neighbourList = new ArrayBuffer[ActorRef]()
  var live:Int=1
  var NoChange: Int=0
  var gossipCount: Int=0


  def receive = {
    case IntializeNeighbours(neighbour) =>
      neighbourList ++= neighbour
    
      
    case InitializeSW(s,w) =>
      Sindex=s
      Wvalue=w
      latestRatio=s/w
      previousRatio= s/w

    case PUSH(s,w) =>
  
      if(live==1)
      {
       // if(Wvalue==1)
       	println("S/W received by the Actor"+ this.self)
   
        previousRatio= latestRatio
        Sindex = (s + Sindex)/2
        Wvalue = (w + Wvalue)/2
        latestRatio= Sindex/Wvalue

        if (abs(latestRatio- previousRatio) <= 1e-10 &&  w!=0)		// CHANGE IN THE PREVIOUs AND CURRENT S/W RATIO
          NoChange += 1
        if (NoChange<3)				
        {
          neighbourList(Random.nextInt(neighbourList.length)) ! PUSH(Sindex,Wvalue)
        }

        else {						// IF MORE THAN 3 NO CAHNGE IN RATIO THAN NOTIFY MASTER AND TERMINATE THE SYSTEM

          live=0
         // for(i<-0 to neighbourList.length-1)
           // neighbourList(i) ! RemoveActor(this.self)
            //println("The sum value -->" + Sindex + "and weight value" + Wvalue   )
          println("S/W Ratio: " + (Sindex/Wvalue))
            master ! Completed
            
        }
      }

    case RemoveActor(callingActor) =>
      
      neighbourList= neighbourList - callingActor
     if(neighbourList.length==0)				//REMOVE THE TERMINATED ACTOR FROM THE NEIGHBOUR LIST
     {
       live=0
       master ! Completed
       context.stop(self)
       
     }


    case StartGossip =>
      if(live==1) {
        
        if(gossipCount<10 && neighbourList.length>0) {
          if(gossipCount==0)
          {
            //println("messagerecieve by "+ this.self)		//RECEIVED GOSSIP FIRST TIME
            master ! GossipReached
           
          }
          gossipCount +=1
          neighbourList(Random.nextInt(neighbourList.length)) ! StartGossip	// SEND GOSSIP TO RANDOM NEIGHBOUR
          // SEND GOSSIP ON A SCHEDULED BASIS TO THE NEIGHBOURS
          context.system.scheduler.schedule(10 milliseconds , 50 milliseconds, neighbourList(Random.nextInt(neighbourList.length)), 		StartGossip) 
        }
        else {
       for (i<- 0 until neighbourList.length )
           neighbourList(i) !RemoveActor(this.self)		//NOTIFY NEIGHBOURS WHEN TERMINATED
              live=0
              master ! Completed				//NOTIFY MASTER WHEN TERMINATED
              context.stop(self)
            }
      }
      else
      {
       for (i<- 0 until neighbourList.length )
           neighbourList(i) !RemoveActor(this.self)
           master ! Completed
      }
      
       case KillNode =>					// USED TO DELIBRATELY STOP THE NODE BY MASTER TO ANALYSE THE FALIURE MODEL
         live=0
         context.stop(self)

  }
}
