
var net = require("net")

var client = net.createConnection(4321, "localhost").on("connect", function (){
               client.write("hello"); 
             }).on("data", function(data){
               client.write(data);  
             })
