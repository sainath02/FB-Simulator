
import java.util
import java.util.concurrent.{TimeUnit, ConcurrentHashMap}
//import
import akka.actor._
import akka.pattern._
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Await}
import scala.util.{Failure, Success, Random}
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
import scala.concurrent.ExecutionContext.Implicits.global
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec._;
//import org.apache.commons.codec.binary.Base64
import java.security._
import akka.routing.RoundRobinPool
import akka.actor.PoisonPill
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;
import org.apache.commons.codec.binary.Base64

/**
  * Created by SIVA on 11/20/2015.
  */


case class FriendsList(list: Array[String])
case class GetPagePostsResults(user_id: Int, post_message: String)
case class UserPost(postId:Int, post:String)
case class POSTPostBody(postId:Int, post:String)
case class UserPosts1(list : Array[POSTPostBody])
case class KeyListsToFriends(userId:Int, key:String)

case class AuthReq(userId: Int, key: String)
case class AuthToken(userId: Int, token: String)


object MyJsonProtocol extends DefaultJsonProtocol{

  implicit val GetPagePostsResultsFormat = jsonFormat2(GetPagePostsResults)
  implicit val postFormat = jsonFormat2(UserPost)
  implicit object FrL extends RootJsonFormat[FriendsList]{
    def read (v:JsValue) = FriendsList(v.convertTo[Array[String]])
    def write (f:FriendsList) = f.list.toJson
  }
  implicit object userPostsFormat1 extends RootJsonFormat[UserPosts1]{
    def read (v:JsValue) = UserPosts1(v.convertTo[Array[POSTPostBody]])
    def write (f:UserPosts1) = f.list.toJson
  }
  implicit val POSTpostFormat = jsonFormat2(POSTPostBody)
  implicit val AuthReqFormat = jsonFormat2(AuthReq)
  implicit val AuthTokenFormat = jsonFormat2(AuthToken)

}

object Client extends App{

  implicit val system = ActorSystem("FaceBookClientSimulator")
  val webClient = new SprayWebClient()(system)
  val server = "localhost:8080"
  var num_of_users:Int = 1000
  MasterSecurity.createFriendsRigistry(num_of_users)

  /*
  "no_of_users" can be dynamic
   Just a change here will be applied everywhere.
   Currently Server Network is set to 10000 users.
   makesure you have same less numbers of users than mentioned in Server network.
  */
  var startTime=System.currentTimeMillis
  var userActor = new ArrayBuffer[ActorRef]

  import MyJsonProtocol._
  implicit val timeout = Timeout(20 seconds)
  var deadline = 10.seconds.fromNow

  //Retriveing User data through loop
  println("Starting Actors")
  var i:Int = 0
  while (i<num_of_users){
    var a:ActorRef=system.actorOf(Props(new MasterActor()),name = "Master"+i)
    userActor += a
    i=i+1
  }

  //generate Keys
  println("Generating Public & Private keys for all actors")
  for( userId <- 0 until num_of_users ) {
    userActor(userId) ? generatekeys(userId.toInt)
  }
  while(!(MasterSecurity.RSAPublicRegistry.size()== num_of_users)){}

  //sample to check whether data is retrieved properly
//  println("Started Sample Checking")
//  sampleCheck()

  //Authenticate all Users
  println("AuthenticatingAllUsers")
  for( userId <- 0 until num_of_users ) {
    val future = userActor(userId) ? authenticate(userId.toInt)
    Await.result(future, timeout.duration)
  }

  deadline = 15.seconds.fromNow
  Thread.sleep(deadline.timeLeft.toMillis)

  //get user Profiles
  println("Getting User profiles")
  for( userId <- 0 until num_of_users ) {
    val future = userActor(userId) ? getUserProfile(userId.toInt)
    Await.result(future,timeout.duration)
  }

  //get user friends list
  println("getting FriendsList and Updating the friendsList")
  for( userId <- 0 until num_of_users ) {
    val future = userActor(userId) ? getFriendsList(userId.toInt)
    Await.result(future,timeout.duration)
  }
  deadline = 10.seconds.fromNow
  Thread.sleep(deadline.timeLeft.toMillis)

  //Post on user Profile
  def randomString(length: Int) = {
    val r = new scala.util.Random
    val sb = new StringBuilder
    for (i <- 1 to length) {
      sb.append(r.nextPrintableChar)
    }
    sb.toString
  }

  println("posting messages on users pages")
  for( userId <- 0 until num_of_users ) {
    //val randomPost:String = "User "+ userId +" Posting: Hello How are you? This is randomPost " + randomString(5)
    val randomPost:String = "User "+ userId +" Posting: Hello How are you? This is randomPost " + randomString(5)
    val future = userActor(userId) ? postToUserPage(userId.toInt, randomPost)
    Await.result(future,timeout.duration)
  }

  deadline = 10.seconds.fromNow
  Thread.sleep(deadline.timeLeft.toMillis)

  //get user Posts
  println("About to Print user posts")
  for( userId <- 0 until num_of_users ) {
    val future = userActor(userId) ? getUserPosts(userId.toInt)
    Await.result(future,timeout.duration)
  }

  deadline = 10.seconds.fromNow
  Thread.sleep(deadline.timeLeft.toMillis)

  //get user Pages
  println("getting user pages")
  for( userId <- 0 until num_of_users ) {
    val future = userActor(userId) ? getUserPage(userId.toInt)
    Await.result(future,timeout.duration)
  }

  var timeTaken = System.currentTimeMillis - startTime
  println("Total time taken: "+timeTaken)

  var cipher:Cipher = null

  def sampleCheck() ={
    //var a:ActorRef=system.actorOf(Props(new MasterActor()),name = "SampleMaster"+ 10)
    //var userId = Random.nextInt(num_of_users)
    var userId:Int = 10
    //a ? generatekeys(userId.toInt)

    val fun0 = userActor(10) ? check()
    Await.result(fun0,timeout.duration)
    val fun6 = userActor(10) ? authenticate(10)
    Await.result(fun6,timeout.duration)
    val fun1 = userActor(10) ? getFriendsList(userId)
    Await.result(fun1,timeout.duration)
    deadline = 10.seconds.fromNow
    Thread.sleep(deadline.timeLeft.toMillis)
    val fun2 = userActor(10) ? getUserProfile(userId.toInt)
    Await.result(fun2,timeout.duration)
    val textBeforeEncrypt = userId+ " Posting: Hello How are you?"
    val fun3 = userActor(10) ? postToUserPage(userId.toInt, textBeforeEncrypt)
    Await.result(fun3,timeout.duration)
    deadline = 10.seconds.fromNow
    Thread.sleep(deadline.timeLeft.toMillis)
    val fun4 = userActor(10) ? getUserPosts(userId.toInt)
    Await.result(fun4,timeout.duration)
    val temp = MasterSecurity.userFriendsRegistry.get(userId)(0)
    val fun5 = userActor(temp) ? getUserPage(temp)
    Await.result(fun5,timeout.duration)

  }

}

class MasterActor extends Actor {

  var numOfUsersInNetwork = 1000
  var privKey:PrivateKey = null

  def receive = {
    case check() => {
      println("Started")
      sender ! "hello"
    }
    case generatekeys(id) =>{
      privKey = MasterSecurity.generateKeys(id)
      //println(privKey)
    }
    case getFriendsList(id) => {
      sender ! FriendsList(id)
    }

    case getUserProfile(id) => {
      sender ! UserProfile(id)
    }
    case getUserPosts(id) => {
      sender ! UserPosts(id)
    }
    case getUserPage(id) => {
      sender ! UserPage(id)
    }
    case postToUserPage(id , randomPost) => {
      sender ! postOnUserPage(id, randomPost)
    }

    case authenticate(id) => {
      sender ! AuthenticateRequest(id)
    }
  }

  import MyJsonProtocol._


  def FriendsList( userId : Int): Unit = {
    val pipeline = sendReceive
    val chk: Future[HttpResponse] = pipeline{Get("http://localhost:8080/users/"+ userId + "/friends")}
    //println("sent http request")
    // wait for Future to complete
    chk.onComplete{
      x=>{
        x.foreach{
          res => (res.entity.asString)
            var y = res.entity.asString.parseJson.convertTo[FriendsList]
            //println(y.list(0))
            MasterSecurity.updateFriendsList(userId, y)
            //println(MasterSecurity.userFriendsRegistry.get(userId)(0))
        }
      }
    }
  }

  def decryptUsingPrivate(text: String): String ={
    var cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.DECRYPT_MODE,privKey)
    val encrypttext = text.getBytes("ISO-8859-1")
    var decryptedSecretKey = cipher.doFinal(encrypttext)
    new String(decryptedSecretKey)
  }

  var pipelineUP = sendReceive

  def AuthenticateRequest( userId : Int): Unit = {
    val messageId = numOfUsersInNetwork+Random.nextInt(1000000)
    var encryptedContent:String = null
    val publicKey = MasterSecurity.RSAPublicRegistry.get(userId)
    val publicKeyStr = Base64.encodeBase64String(publicKey.getEncoded())
    val pipeline = sendReceive ~> unmarshal[HttpResponse]
    val chk: Future[HttpResponse] = pipeline{
      Post("http://localhost:8080/users/"+ userId + "/authRequest", AuthReq(userId, publicKeyStr))
    }

    chk onComplete {
      case Success(response) => {
        println(response)
        encryptedContent = response.entity.asString
        var decryptedContent = decryptUsingPrivate(encryptedContent)
        AuthenticateResponse(userId, decryptedContent)
      }
      case Failure(error) => println("Error in post USER " + userId + " Post. Error message is: " + error.getMessage)
    }
  }

  def AuthenticateResponse(userId:Int, decryptedContent:String):Unit = {
    val pipeline = sendReceive ~> unmarshal[HttpResponse]
    val chk: Future[HttpResponse] = pipeline {
      Post("http://localhost:8080/users/" + userId + "/authVerify", AuthToken(userId, decryptedContent))
    }

    chk onComplete {
      case Success(response) => {
        println(response)
        if(response.entity.asString.toBoolean){
          var httpCookie = response.headers.collect { case spray.http.HttpHeaders.`Set-Cookie`(hc) => hc }
          pipelineUP = addHeader(spray.http.HttpHeaders.Cookie(httpCookie)) ~> (sendReceive)
          println(("Added cookie -> " + httpCookie + " to pipeline header!"))
          true
        }else{
          println("Failed to authenticate USER " + userId)
          pipelineUP = null
          false
        }
      }
      case Failure(error) => println("Error in receiving USER " + userId + " Profile. Error message is: " + error.getMessage)
    }
  }

  def UserProfile( userId : Int): Unit = {

    //pipelineUP = sendReceive
    val chk: Future[HttpResponse] = pipelineUP{Get("http://localhost:8080/users/"+ userId + "/profile")}

    chk onComplete {
      case Success(response) => println(response)
      case Failure(error) => println("Error in receiving USER " + userId + " Profile. Error message is: " + error.getMessage)
    }
  }

  def postOnUserPage( userId : Int, userPost :String): Unit = {
    val messageId = numOfUsersInNetwork+Random.nextInt(1000000)
    val encryptedPost = MasterSecurity.encryptMsg(userId, userPost,messageId)
    val pipeline = sendReceive ~> unmarshal[HttpResponse]
    val chk: Future[HttpResponse] = pipeline{
      Post("http://localhost:8080/users/"+ userId + "/addpost", UserPost(messageId, encryptedPost))
    }

    chk onComplete {
      case Success(response) => println(response)
      case Failure(error) => println("Error in post USER " + userId + " Post. Error message is: " + error.getMessage)
    }
  }


  var getPostsOutput = new ArrayBuffer[String]

  def UserPosts( userId : Int): Unit = {
    val pipeline = sendReceive
    val chk: Future[HttpResponse] = pipeline {
      Get("http://localhost:8080/users/" + userId + "/posts")
    }

    chk.onComplete{
      x=>{
        x.foreach{
          res => (res.entity.asString)
            var x = res.entity.asString.parseJson.convertTo[UserPosts1]
            //println(x.list(0).post)
            for(i<-0 until x.list.size){
             val post = x.list(i).post
             val id = x.list(i).postId
             MasterSecurity.decrypetTheText(id, post, privKey, userId)
            }
            //MasterSecurity.decryptAndPrintPosts(x.list(0))
        }
      }
    }
  }

  def UserPage( userId : Int): Unit = {

    val pipeline = sendReceive
    val chk: Future[HttpResponse] = pipeline{Get("http://localhost:8080/users/"+ userId + "/homepage")}
    println("printingPages of User "+ userId)

    chk.onComplete{
      x=>{
        x.foreach{
          res => (res.entity.asString)
            var x = res.entity.asString.parseJson.convertTo[UserPosts1]
            //println(x.list(0).post)
            for(i<-0 until x.list.size){
              val post = x.list(i).post
              val id = x.list(i).postId
              MasterSecurity.decrypetTheText(id, post, privKey, userId)
            }
          //MasterSecurity.decryptAndPrintPosts(x.list(0))
        }
      }
    }
  }
}

trait WebClient{
  def get(url:String): Future[String]
}

class SprayWebClient(implicit system: ActorSystem) extends WebClient{
  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  def get(url: String): Future[String] = {
    val futureResponse = pipeline(Get(url))
    futureResponse.map(_.entity.asString)
  }
}

object MasterSecurity{

  import MyJsonProtocol._

  var RSAPublicRegistry = new ConcurrentHashMap[Int, PublicKey]
  var userFriendsRegistry = new ConcurrentHashMap[Int, ArrayBuffer[Int]]
  var userPostsRegistry = new ConcurrentHashMap[Int, ArrayBuffer[KeyListsToFriends]]
  var cipher:Cipher = null
  var charset: String = "ISO-8859-1"

  def createFriendsRigistry(num_of_users:Int):Unit={
    for( userId <- 0 until num_of_users ){
      if(!userFriendsRegistry.containsKey(userId)){
        var friendlist : ArrayBuffer[Int]= new ArrayBuffer[Int]
        userFriendsRegistry.put(userId, friendlist)
      }
    }
  }

  def generateKeys(userId:Int): PrivateKey ={
    var keygen = KeyPairGenerator.getInstance("RSA")
    keygen.initialize(1024)
    val keyPair:KeyPair = keygen.generateKeyPair();
    var pubKey = keyPair.getPublic
    RSAPublicRegistry.put(userId, pubKey)
    return keyPair.getPrivate
//    println(keyPair.getPublic)
//    println(keyPair.getPrivate)
  }

  def encryptMsg(userId:Int, message:String, messageId:Int):String = {
    val random = new SecureRandom()
    val secretKey:Array[Byte] = Array.fill[Byte](16)(0)
    random.nextBytes(secretKey)
    val AESKey = new SecretKeySpec(secretKey,"AES")

    updateAESKeystoFriends(userId, messageId, secretKey)
    cipher = Cipher.getInstance("AES");

    val plainTextByte= message.getBytes()
    cipher.init(Cipher.ENCRYPT_MODE, AESKey)
    val encryptedByte = cipher.doFinal(plainTextByte)
    val encoder = new String(encryptedByte, "ISO-8859-1")
    //val encoder = Base64.encodeBase64String(encryptedByte)
    //println(encoder.encodeToString(encryptedByte))
    return encoder
  }

  def updateAESKeystoFriends(userId:Int, messageId:Int, secretKey: Array[Byte]): Unit ={
    var keylist : ArrayBuffer[KeyListsToFriends] = new ArrayBuffer[KeyListsToFriends]

    for(i <- 0 until userFriendsRegistry.get(userId).size){
      var friend = userFriendsRegistry.get(userId)(i)
      var key = RSAPublicRegistry.get(friend)

      cipher = Cipher.getInstance("RSA")
      cipher.init(Cipher.ENCRYPT_MODE, key)
      val encryptedByte = cipher.doFinal(secretKey)
      val encrypted = new String(encryptedByte, "ISO-8859-1")
      keylist += KeyListsToFriends(friend, encrypted)
      //userPostsRegistry.get()
    }
    var key = RSAPublicRegistry.get(userId)
    cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val encryptedByte = cipher.doFinal(secretKey)
    //val encoder = Base64.encodeBase64String(encryptedByte)
    val encoder = new String(encryptedByte, "ISO-8859-1")
    keylist += KeyListsToFriends(userId, encoder)
    userPostsRegistry.put(messageId, keylist)
  }


  def decrypetTheText(messageId:Int, post:String, privateKey: PrivateKey, userId:Int): Unit ={

    //decrypting the AES key with private key
    cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.DECRYPT_MODE,privateKey)

    var ciphertext = findAESkey(messageId,userId)
    if(ciphertext.equalsIgnoreCase("Not a Friend"))return

    val encrypttext = ciphertext.getBytes("ISO-8859-1")
    var decryptedSecretKey:Array[Byte] = null
    try{
      decryptedSecretKey = cipher.doFinal(encrypttext)
    }catch {
      case t:Throwable => return
    }

    var originalAES = new SecretKeySpec(decryptedSecretKey, "AES")

    cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE,originalAES)
    var decryptedText:Array[Byte]=null
    try{
      decryptedText = cipher.doFinal(post.getBytes("ISO-8859-1"))
    }catch {
      case t:Throwable => return
    }

    //var decryptedText = cipher.doFinal(post.getBytes("ISO-8859-1"))
    var finalText = new String(decryptedText,"ISO-8859-1")
    println("Post: " + finalText)
  }

  def findAESkey(messageId:Int, frnUserId:Int): String ={
    if(!userPostsRegistry.containsKey(messageId)) return "Not a Friend"
    for(i <- 0 until userPostsRegistry.get(messageId).size){
      if(userPostsRegistry.get(messageId)(i).userId == frnUserId){
        var ciphertext = userPostsRegistry.get(messageId)(i).key
        return ciphertext
      }
    }
    return "Not a Friend"
  }

  def updateFriendsList(userId:Int,friendslist:FriendsList): Unit ={

    //var friendsList:Array[String] = friendslist.list
    var friendlist : ArrayBuffer[Int] = new ArrayBuffer[Int]
    for(i <- 0 until friendslist.list.size){
      val friend = friendslist.list(i).split(" ")
      val friendId:Int = friend(1).toInt
      friendlist += friendId
    }
    userFriendsRegistry.put(userId, friendlist)
  }
}



sealed trait Facebook
case class check() extends Facebook
case class authenticate(userId:Int) extends Facebook
case class generatekeys(userId:Int) extends Facebook
case class getFriendsList(userId: Int) extends Facebook
case class getUserProfile(userId: Int) extends Facebook
case class getUserPosts(userId: Int) extends Facebook
case class getUserPage(userId: Int) extends Facebook
case class postToUserPage(userId: Int, userPost :String) extends Facebook
