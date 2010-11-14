var strict = false; //use strict vim bindings

//(selected RID)
var selectedRID = -1;
//(highlighting)
var last = null;
var savedClass = null;
var lastI = -1;

function byId (the_id) {
	if (typeof the_id != 'string') {
		return the_id;
	}
	if (typeof document.getElementById != 'undefined') {
		return document.getElementById(the_id);
	} else if (typeof document.all != 'undefined') {
		return document.all[the_id];
	} else if (typeof document.layers != 'undefined') {
		return document.layers[the_id];
	} else {
		return null;
	}
}

function select(i){
	//(update variables)
	obj = byId("tableRun").rows[i+1]
	lastI = i;
	//(clear last selection)
	if (last) {
		for(i=0;i<last.cells.length;i++){
			last.cells[i].className = savedClass;
		}
	}
	//(style selection)
	savedClass = obj.cells[0].className;
	last = obj;
	selectedRID = obj.cells[0].innerHTML;
	for(i=0;i<obj.cells.length;i++){
		obj.cells[i].className = "selected";
	}
	//(make ajax call)
	$.post("details", 
		{ rid: selectedRID},
		gotDetails,
		"json");
}
function goToRid(rid){
	//(vim compatibility :0=>top)
	if(rid == 0){
		select(0);
		window.scrollTo(0,0);
		return;
	}
	//(find id)
	i = 0;
	$("table#tableRun tr").each( function(i,row){
		if(rid == parseInt(row.cells[0].innerHTML)){
			select(i-1);
			return;
		}
		i += 1;
	})
}

var selectedInput = 0;
function selectInput(x) {
	if(x == 0){
		$("a#showOptions").css("font-weight","bold");
		$("a#showParams").css("font-weight","normal");
		$("a#showAll").css("font-weight","normal");
		$("div#detailsInput").html($("div#detailsInputOptions").html());
	} else if(x == 1){
		$("a#showOptions").css("font-weight","normal");
		$("a#showParams").css("font-weight","bold");
		$("a#showAll").css("font-weight","normal");
		$("div#detailsInput").html($("div#detailsInputParams").html());
	} else if(x == 2){
		$("a#showOptions").css("font-weight","normal");
		$("a#showParams").css("font-weight","normal");
		$("a#showAll").css("font-weight","bold");
		$("div#detailsInput").html("<table>" +
			$("table#fillParams").html() + "\n" +
			$("table#fillOptions").html() + "\n" +
			"</table>");
	} else{
		alert("Internal error! Lets say... code 235621");
		return;
	}
	selectedInput = x;
}


function cmdMode(){
	return $("div#cmdHolder").css("display") != "none"
}
function normalMode(){
	return ($("div#cmdHolder").css("display") == "none")
}

function onKey(e){
	var k = (window.event) ? event.keyCode : e.keyCode;
	switch(k){
	case 38: //up
	case 75:
		if(normalMode()){
			cand = Math.max(0,lastI-(e.shiftKey ? 10 : 1));
			select(cand);
		}
		e.preventDefault();
		break;
	case 40: //down
	case 74:
		if(normalMode()){
			cand = Math.min(byId("tableRun").rows.length-1,
				lastI+(e.shiftKey ? 10 : 1));
			select(cand);
		}
		e.preventDefault();
		break;
	case 27: //esc
		$("div#cmdHolder").css("display","none");
		$("input#cmd").val("");
		break;
	case 186: //[semi-]colon
		if(!strict || e.shiftKey){
			$("div#cmdHolder").css("display","inherit");
			$("input#cmd").focus();
		}
		break;
	case 13: //enter
		if(cmdMode()){
			cmd = $("input#cmd").val();
			if(cmd.trim() == ''){
			//--Parse Locally
			//(integer?)
			}else if(!isNaN(cmd)){
				goToRid(parseInt(cmd));
			//--Send to Server
			} else {
			}
			$("div#cmdHolder").css("display","none");
			$("input#cmd").val("");		
		}
		break;
	//--Vim Shortcuts
	case 71: //g
		if(e.shiftKey && normalMode()){
			select(byId("tableRun").rows.length-2);
		}
	default:
		break;
	}
}

var gotDetails = function(json){
	//(header)
	$("span#fillRID").html( json[0].rid );
	$("span#fillName").text( json[0].name );
	//(options)
	optTable = ""
	$.each(json[1].options, function(i,opt){
		optTable += "<tr class=\"optRow\">"
			+"<td class=\"optKey\">"+opt.key+"</td>"
			+"<td class=\"optValue\">"+opt.value+"</td>\n"
	});
	$("table#fillOptions").html( optTable );
	//(parameters)
	paramTable = ""
	$.each(json[1].params, function(i,param){
		paramTable += "<tr class=\"paramRow\">"
			+"<td class=\"paramKey\">"+param.key+"</td>"
			+"<td class=\"paramValue\">"+param.value+"</td>\n"
	});
	$("table#fillParams").html( paramTable );
	//(results)
	resultTable = ""
	$.each(json[1].results, function(i,result){
		resultTable += "<tr class=\"paramRow\">"
			+"<td class=\"resultKey\">"+result.key+"</td>"
			+"<td class=\"resultValue\">"+result.value+"</td>\n"
	});
	$("table#fillResults").html( resultTable );
	selectInput(selectedInput);
}

$(document).ready(function() {
	//(key listeners)
	document.onkeyup = onKey
	//(select)
	select(0);
	selectInput(0);
	$("a#showOptions").click(function(e){ e.preventDefault(); selectInput(0); })
	$("a#showParams").click(function(e){ e.preventDefault(); selectInput(1); })
	$("a#showAll").click(function(e){ e.preventDefault(); selectInput(2); })
	//(get details)
	$("input#test").click(function(){
		if(selectedRID == -1){
			alert("Please select a run!");
			return;
		}
		$.post("details", 
			{ rid: selectedRID},
			gotDetails,
			"json");
	})
}); 

