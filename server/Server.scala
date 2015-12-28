
import java.security.Security
import java.security.{KeyFactory, PublicKey}
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.{TimeUnit, ConcurrentHashMap}

import akka.actor._
import akka.pattern._
import spray.httpx.SprayJsonSupport
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.util.Random
import spray.routing.SimpleRoutingApp
import spray.routing._
import akka.io.IO
import spray.can.Http
import spray.http._
import scala.collection.mutable.ArrayBuffer
import MediaTypes._
import spray.json._
import spray.http.{MediaTypes, MediaType}
import java.util._
import akka.util.Timeout
import scala.concurrent.duration._
import spray.httpx.SprayJsonSupport._
import scala.concurrent.duration._
//import org.json4s._
import spray.json.DefaultJsonProtocol._
import akka.routing.RoundRobinRouter
import com.typesafe.config.ConfigFactory
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import sun.misc.BASE64Decoder
import org.apache.commons.codec.binary.Base64


/**
  * Created by SIVA on 11/18/2015.
  */


case class Friend(userId: Int)
case class FriendsList(list: Array[String])
case class UserProfile(userId: Int, userName: String, userAge: Int, userSex: String)
case class UserPosts(list: Array[String])
case class POSTPostBody(postId:Int, post:String)
case class PAGEPostBody(postId: Int, post:String)

case class AuthReq(userId: Int, key: String)
case class AuthToken(userId: Int, token: String)

case class UserPage(list : Array[PAGEPostBody])
case class UserPosts1(list : Array[POSTPostBody])

sealed trait FacebookServer
case class getFriends(userId: Int) extends FacebookServer
case class getUserProfile(userId: Int) extends FacebookServer
case class getUserPosts(userId: Int) extends FacebookServer
case class getUserPage(userId: Int) extends FacebookServer
case class postUserPost(userId: Int, post: String, post_id:Int) extends FacebookServer

object MyJsonProtocol extends DefaultJsonProtocol{
  implicit val userFriendFormat = jsonFormat1(Friend)
  implicit val userProfileFormat = jsonFormat4(UserProfile)
  //implicit val userPostsFormat = jsonFormat4(UserProfile)
  //implicit val userPostsFormat1 = jsonFormat1(UserPosts1)
  implicit object userPostsFormat extends RootJsonFormat[UserPosts]{
    def read (v:JsValue) = UserPosts(v.convertTo[Array[String]])
    def write (f:UserPosts) = f.list.toJson
  }
  implicit object FrL extends RootJsonFormat[FriendsList]{
    def read (v:JsValue) = FriendsList(v.convertTo[Array[String]])
    def write (f:FriendsList) = f.list.toJson
  }
  implicit object userPageFormat extends RootJsonFormat[UserPage]{
    def read (v:JsValue) = UserPage(v.convertTo[Array[PAGEPostBody]])
    def write (f:UserPage) = f.list.toJson
  }
  implicit object userPostsFormat1 extends RootJsonFormat[UserPosts1]{
    def read (v:JsValue) = UserPosts1(v.convertTo[Array[POSTPostBody]])
    def write (f:UserPosts1) = f.list.toJson
  }
  implicit val postFormat = jsonFormat2(POSTPostBody)
  implicit val pagepostFormat = jsonFormat2(PAGEPostBody)
  implicit val AuthReqFormat = jsonFormat2(AuthReq)
  implicit val AuthTokenFormat = jsonFormat2(AuthToken)
}

object Server extends App with SimpleRoutingApp{

  var serverNode = new ArrayBuffer[ActorRef]
  var toBeVerifiedTokens = new ConcurrentHashMap[Int, String]

  implicit val actorSystem = ActorSystem("MasterFacebookSystem")
  var Master=actorSystem.actorOf(Props(new MasterActor()),name = "Master")
  Master ! "start"

  val r = scala.util.Random
  implicit val timeout = Timeout(10 seconds)

  import MyJsonProtocol._
  startServer(interface = "localhost", port = 8080){
    path("hello"){
      get{
        complete{"Welcome to FB simulator"}
      }
    }~
    path( "users"/ """^[a-zA-Z0-9]*$""".r / "authRequest"){userId=>
      entity(as[AuthReq]) { auth =>
        complete {
          var uuid = UUID.randomUUID().toString()
          var encryptedContent = encryptRSA(uuid, decodeKey(auth.key))
          toBeVerifiedTokens.put(userId.toInt, uuid)
          encryptedContent
          //"Hello"
        }
      }
    }~
    path( "users"/ """^[a-zA-Z0-9]*$""".r / "authVerify"){userId=>
      entity(as[AuthToken]) { auth =>
        if(toBeVerifiedTokens.get(userId.toInt).equals(auth.token)){
          toBeVerifiedTokens.remove(userId.toInt)
          //complete("Success")
          setCookie(HttpCookie("userIdToken", userId)) {
            println("User verified on server : " + userId)
            complete("true")
          }
        }else{
          println("User rejected on server")
          complete("false")
        }
      }
    }~
    path("users"/ """^[a-zA-Z0-9]*$""".r / "friends"){userId =>
      get{
        respondWithMediaType(MediaTypes.`application/json`){
          complete{
            val future = Master ? getFriends(userId.toInt)
            val result:FriendsList = Await.result(future, timeout.duration).asInstanceOf[FriendsList]
            result
          }
        }
      }
    }~
    path("users"/ """^[a-zA-Z0-9]*$""".r / "posts"){userId =>
      get{
        complete{
          val future = Master ? getUserPosts(userId.toInt)
          val result:UserPosts1 = Await.result(future, timeout.duration).asInstanceOf[UserPosts1]
          result
        }
      }
    }~
    path("users"/ """^[a-zA-Z0-9]*$""".r / "profile"){userId =>
      jsonRes {
        get{
          complete{
            val future = Master ? getUserProfile(userId.toInt)
            val result:UserProfile = Await.result(future, timeout.duration).asInstanceOf[UserProfile]
            result
          }
        }
      }
    }~
    path("users"/ """^[a-zA-Z0-9]*$""".r / "homepage"){userId =>
      get{
        respondWithMediaType(MediaTypes.`application/json`){
          complete{
            val future = Master ? getUserPage(userId.toInt)
            val result:UserPage = Await.result(future, timeout.duration).asInstanceOf[UserPage]
            result
          }
        }
      }
    }~
    path("users"/"""^[a-zA-Z0-9]*$""".r/"addpost" ){userId =>
      post{
        entity(as[POSTPostBody]){(post)=>
          val newPost = post.post
          val post_id = post.postId
          //val postId = post.
          complete{
            Master ? postUserPost(userId.toInt, newPost, post_id)
            "OK "
          }
        }
      }
    }
  }

  def encryptRSA(uuid: String, key: PublicKey): String ={
    var cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    var cipherText = cipher.doFinal(uuid.getBytes())
    val encoder = new String(cipherText, "ISO-8859-1")
    return encoder
  }
  def decodeKey(publicKey: String): PublicKey ={
    var publicBytes = Base64.decodeBase64(publicKey);
    var keySpec = new X509EncodedKeySpec(publicBytes);
    var keyFactory = KeyFactory.getInstance("RSA");
    var pubKey = keyFactory.generatePublic(keySpec);
    pubKey
  }
  def jsonRes(route: Route) = {
    cookie("userIdToken") { cookieUserId =>
      respondWithMediaType(MediaTypes.`application/json`) {
        detach() {
          route
        }
      }
    }
  }
}



case class friends(friendsList:ArrayBuffer[Integer])
case class friendOf(friendOfList:ArrayBuffer[Integer])
case class timeline(timeline:ArrayBuffer[String])
case class homepage(Homepage:ArrayBuffer[PAGEPostBody])

class MasterActor extends Actor {

  var no_of_users=1000
  /* "no_of_users" can be dynamic
  Just a change here will be applied everywhere.
  makesure you have same number or less num of users in Client network.
   */
  import MyJsonProtocol._

  var userRegistry = new ConcurrentHashMap[Integer,ArrayBuffer[String]]
  var friends = new ArrayBuffer[Integer]
  var userProfileList = new ArrayBuffer[UserProfile]
  var userFriends = new ConcurrentHashMap[Int,ArrayBuffer[String]]
  var friendOf = new ArrayBuffer[Integer]
  var userFriendOf = new ConcurrentHashMap[Int,ArrayBuffer[Int]]
  var userPostsList = new ConcurrentHashMap[Int,ArrayBuffer[POSTPostBody]]
  var userPageList = new ConcurrentHashMap[Int,ArrayBuffer[PAGEPostBody]]
  var startTime=System.currentTimeMillis

  var num_of_friends1 =80
  var num_of_friends2 = 40
  var num_of_friends3 = 20
  var num_of_friends4 = 13
  var num_of_friends5 = 8
  var num_of_friends6 = 4

  def returnparameter(i: Int): Integer = {
    var parameter = 0
    if (i >= 0 && i < 25)parameter = 1
    else if (i >= 25 && i < 50)
      parameter = 2
    else if (i >= 50 && i < 100)
      parameter = 3
    else if (i >= 100 && i < 200)
      parameter = 4
    else if (i >= 200 && i < 400)
      parameter = 5
    else if (i >= 400 && i < 1000)
      parameter = 6
    return parameter
  }

  def generateFriendsList():Unit = {

    var permachine=no_of_users
    for( userId <- 0 until no_of_users ){
      if(!userFriends.containsKey(userId)){
        var friendList : ArrayBuffer[String]=new ArrayBuffer[String]
        userFriends.put(userId, friendList)
      }
    }
    for( userId <- 0 until no_of_users )
    {
      var mod=userId%permachine
      var num_of_friends:Int = 0
      var parameter:Int=returnparameter(mod)
      parameter match{
        case 0 => {
          num_of_friends=Random.nextInt(num_of_friends6)
        }
        case 6 => {
          num_of_friends=Random.nextInt(num_of_friends6)
        }
        case 5 => {
          num_of_friends=num_of_friends6+Random.nextInt(num_of_friends5-num_of_friends6)
        }
        case 4 =>{
          num_of_friends=num_of_friends5+Random.nextInt(num_of_friends4-num_of_friends5)
        }
        case 3 => {
          num_of_friends=num_of_friends4+Random.nextInt(num_of_friends3-num_of_friends4)
        }
        case 2 => {
          num_of_friends=num_of_friends3+Random.nextInt(num_of_friends2-num_of_friends3)
        }
        case 1 =>{
          num_of_friends=num_of_friends2+Random.nextInt(num_of_friends1-num_of_friends2)
        }
      }
      //userFriends.put(userId,friends(num_of_friends,userId))
      //println(num_of_friends)

      var i:Int =0;
      while(i<(num_of_friends - userFriends.get(userId).size +1)){
        val friend = Random.nextInt(no_of_users)
        //userFriends.get(userId) += friend
        if(!userFriends.get(userId).contains("USER "+friend)){
          userFriends.get(userId) += "USER "+friend
          userFriends.get(friend) += "USER "+userId
        }
        i = i+1
      }
      //println(userFriends.get(userId))
    }
  }

  def friends(num_of_friends:Int,userId:Int):ArrayBuffer[Int]= {
    var friendList:ArrayBuffer[Int]=new ArrayBuffer[Int]
    var i:Int = 0;
    //while loops randomly selects friends from the list of all the users
    while(i<num_of_friends){
      var friend=Random.nextInt(no_of_users)
      if(!friendList.contains(friend) && (userFriends.get(friend)!=null) && userId!=friend){
        friendList += friend
      }
      i = i+1
    }
    //println(friendList)
    return friendList
  }

  def generateUserProfile() = {

    for( userId <- 0 until no_of_users ){
      val userName:String = "USER " + userId
      val age:Int = Random.nextInt(50)+18
      val possibleSex = Seq("Male", "Female")
      val sex = possibleSex(Random.nextInt(possibleSex.length))
      userProfileList += UserProfile(userId,userName, age, sex)
      //userProfileList.put(userId,UserProfile(userId, age, sex))
      //println(userProfileList(userId))
    }
  }

  def generateUserPosts():Unit = {

    for( userId <- 0 until no_of_users ){
      if(!userPostsList.containsKey(userId)){
        var postList : ArrayBuffer[POSTPostBody]=new ArrayBuffer[POSTPostBody]
        userPostsList.put(userId, postList)
        //userPostsList.get(userId) += "Welcome to Facebook USER " + userId
      }
    }
  }

  def generateUserPage():Unit = {
    for( userId <- 0 until no_of_users ){
      if(!userPageList.containsKey(userId)){
        var pageList : ArrayBuffer[PAGEPostBody]=new ArrayBuffer[PAGEPostBody]
        userPageList.put(userId, pageList)
        //userPageList.get(userId) += PAGEPostBody(userId, "USER \" + userId+\" joined Facebook")
      }
    }
  }

  def populateOnFriendsPage(userId :Int, post: String, postId:Int)= {
    var i:Int = 0
    for (i <- 0 until userFriends.get(userId).size){
      val friend = userFriends.get(userId)(i).split(" ")
      val friendId:Int = friend(1).toInt
      //println(friend(1)+ ": "+friendId)
      val samplePost:PAGEPostBody = PAGEPostBody(postId, post)
      userPageList.get(friendId).prepend(samplePost)
    }
  }

  def receive = {
    //case _ =>

    case "start" =>{
      generateFriendsList()
      generateUserProfile()
      generateUserPosts()
      generateUserPage()
    }

    case getFriends(userId: Int) => {
      //println(userFriends.get(userId))
//      var out:UserProfile = userFriends.get(userId).
      sender ! FriendsList(userFriends.get(userId).toArray)
    }

    case getUserProfile(userId: Int) => {
      //println(UserProfile(10,"SIVA" ,20,"Male"))
      //println(userProfileList.size)
      sender ! userProfileList(userId)
    }

    case getUserPage(userId: Int)=>{
      var actualPageList : ArrayBuffer[PAGEPostBody]=new ArrayBuffer[PAGEPostBody]
      var i:Int = 0
      for (i <- 0 until userPageList.get(userId).size){
        //actualPageList += PAGEPostBody(userPageList.get(userId)(i))
        //actualPageList += PAGEPostBody(100, "hello")
        val temp = userPageList.get(userId)
        val temp2 = temp(i)
        actualPageList += temp2
        //actualPageList += PAGEPostBody(userPageList.get(userId)(i))
      }
      sender ! UserPage(actualPageList.toArray)
      //sender ! UserPage(userPageList.get(userId).toArray)
    }

    case getUserPosts(userId: Int)=>{
      var actualPostList : ArrayBuffer[POSTPostBody]=new ArrayBuffer[POSTPostBody]
      var i:Int = 0
      for (i <- 0 until userPostsList.get(userId).size){
        actualPostList += userPostsList.get(userId)(i)
      }
      sender ! UserPosts1(actualPostList.toArray)
      //sender ! UserPosts(userPostsList.get(userId).toArray)
    }

    case postUserPost(userId:Int , post:String, post_id:Int)=>{
      //sender ! FriendsList(userFriends.get(userId).toArray)
      //var samplePost = "USER " + userId + "Posted: " + post
      var samplePost = POSTPostBody(post_id, post)
      userPostsList.get(userId).prepend(samplePost)
      //userPostsList.get(userId) +=: samplePost
      populateOnFriendsPage(userId, post, post_id)
    }
  }
}