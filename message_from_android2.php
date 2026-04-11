<?php 
header('Contant-Type:application/json');
include_once('../config/database.php');
 

// $msg  = htmlspecialchars($_REQUEST['msg'], ENT_QUOTES, 'UTF-8');
$msg = $_REQUEST['msg'];
$operator  = $_REQUEST['op'];
$slid  = $_REQUEST['st']; // flexi id
$pcode  = $_REQUEST['pcode'];
$sendcode  = $_REQUEST['phone']; // message sender
$simno  = $_REQUEST['sim'];
$port  = $_REQUEST['port'];
$sevid  = $_REQUEST['serviceid']; // for mbank use
 

$create_date=date('Y-m-d H:i:s');
$idate = date('Y-m-d');
$time=time();
$time_check=$time-600;
 $curDate   = date('Y-m-d H:i:s');
if(!empty($msg)){
	 
	// recharge message execute
	$successmsg ='success';
	$commission ='commission';
	$accepted ='accepted';
	$successfully ='successfully'; 
	$successbk ='successful.'; 
	$successdbbl ='Success';
	$successbkashLoad ='Received Mobile Recharge request of Tk';
	$successbkashLoad2 ='Received Recharge request of Tk';
	$successnagad ='Mobile Recharge Request Received.';
	$successNagadCI ='Cash-In';
	$successNagads ='Successful.';
	$successNagadOut ='Cash-Out Successful.';
	$successRoketsent ='transferred to';
	$successRoketOut ='Cash-Out'; 
	//$vas ='VAS request for Service';
 
 
	$smsspill = preg_split("/[\s,),:]+/", $msg);
	$smsbks = preg_split("/[\s]+/", $msg); 
	$smsrks = preg_split("/[\s:Tk]+/", $msg); 
   
	
	$reqnumber = null;
	$reqamount = null;
	$qtrxid    = null;
	$simam     = null;

	$flexi_amount = null;
	$user_id = 0;
	$reload_number = null;
	$reload_id = 0;
	$country_id = 0;

	// if (preg_match("/".$vas."/", $msg )) {
	// 	$response = [
	// 		'insert' => 1,
	// 		'msg' =>'Not Insert'
	// 	];
	// 	echo json_encode($response);
	// 	exit();
	// }
  

	if ((preg_match("/".$successfully."/", $msg )) or (preg_match("/".$successbkashLoad2."/", $msg )) or (preg_match("/".$successbkashLoad."/", $msg )) or (preg_match("/".$successRoketOut."/", $msg )) or (preg_match("/".$successmsg."/", $msg )) or (preg_match("/".$successRoketsent."/", $msg )) or (preg_match("/".$successNagadOut."/", $msg )) or (preg_match("/".$successNagads."/", $msg )) or (preg_match("/".$successNagadCI."/", $msg )) or (preg_match("/".$successnagad."/", $msg )) or (preg_match("/".$commission."/", $msg )) or (preg_match("/".$accepted."/", $msg )) or (preg_match("/".$successbk."/", $msg )) or (preg_match("/".$successdbbl."/", $msg ))) {
 
		 
		// gp start
		if($pcode=="GP") {
			if (strpos($msg, "Recharge of") !== false) {
			    $amount_start = "Recharge of ";
			    $amount_end = " BDT";
			    $number_start = "to 88";
			    $number_end = " is successful";
			    $trx_start = "Transaction ID ";
			    $trx_end = ".";
			    $balance_start = "Your new balance is ";
			    $balance_end = " BDT";
			} elseif (strpos($msg, "Promo VAS recharge") !== false) {
			    $amount_start = "Promo VAS recharge of ";
			    $amount_end = " BDT";
			    $number_start = "to 88";
			    $number_end = " is successful";
			    $trx_start = "Transaction ID ";
			    $trx_end = " ";
			    $balance_start = "Your new balance is ";
			    $balance_end = " BDT";
			} elseif (strpos($msg, "Promo recharge") !== false) {
			    $amount_start = "Promo recharge ";
			    $amount_end = " BDT";
			    $number_start = "to 88";
			    $number_end = " is successful";
			    $trx_start = "Transaction ID ";
			    $trx_end = " ";
			    $balance_start = "Your new balance is ";
			    $balance_end = " BDT";
			} elseif (strpos($msg, "PowerLoad Recharge") !== false) {
			    $amount_start = "Recharge request of BDT ";
			    $amount_end = " ( Powerload Offer )";
			    $number_start = "mobile no. ";
			    $number_end = ",";
			    $trx_start = "TxnID ";
			    $trx_end = " is successful.";
			    $balance_start = "Bal is BDT ";
			    $balance_end = ".";
			}
			$msg = $msg." ";
			$gp_values = gp_new_message_method($msg, $amount_start, $amount_end, $number_start, $number_end, $trx_start, $trx_end, $balance_start, $balance_end);
			
			$reqnumber= $gp_values['number'];
			$reqamount= $gp_values['amount'];
			$qtrxid= $gp_values['trxid'];
			$simam= $gp_values['balance'];
			// dd($gp_values);
		} // gp finish
	   
		// robi start
		if($pcode=="RB") {
			if($smsspill[2]=="commission") {
				$reqnumber = $smsspill[14];
				$comamount = $smsspill[4];
				$qtrxid = $smsspill[8];
				$simam = $smsspill[19];

				$reqnumber=getStringBetween($msg, 'customer','. Your');
				$reqamount=getStringBetween($msg, 'commission of','for transfer');
				$qtrxid=getStringBetween($msg, 'transfer ID','has been');
				$simam=getStringBetween($msg, 'new balance is','TAKA.and');
			}else if ($smsspill[4]=="Postpaid") {
				$reqnumber = $smsspill[12];
				$reqamount = $smsspill[10];
				$qtrxid = $smsspill[2];
				$simam = $smsspill[22];
			}else {
				$reqnumber=getStringBetween($msg, 'to','is');
				$reqamount=getStringBetween($msg, 'recharge','Tk to');
				$qtrxid=getStringBetween($msg, 'number is','Your new');
				$simam=getStringBetween($msg, 'balance is','TAKA.');
			}
		} // robi finish

		// blink start
		if($pcode=="BL") {
			if($smsspill[2]=="commission") {
				$reqnumber = $smsspill[14];
				$comamount = $smsspill[4];
				$qtrxid = $smsspill[8];
				$simam = $smsspill[19];

				$reqnumber=getStringBetween($msg, 'customer','. Your');
				$reqamount=getStringBetween($msg, 'commission of','for transfer');
				$qtrxid=getStringBetween($msg, 'transfer ID','has been');
				$simam=getStringBetween($msg, 'new balance is','and');
			}else if ($smsspill[4]=="Postpaid") {
				$reqnumber = $smsspill[12];
				$reqamount = $smsspill[10];
				$qtrxid = $smsspill[2];
				$simam = $smsspill[22];
			}else {
				$reqnumber = $smsspill[8];
				$reqamount=getStringBetween($msg, 'of TK','for');
				$qtrxid=getStringBetween($msg, 'transaction ID','is successful');
				$simam=getStringBetween($msg, 'balance is TK',''); 
			}
		} // blink finish

		// airtel start
		if($pcode=="AT") {
			if($smsspill[2]=="commission") {
				$reqnumber = $smsspill[14];
				$comamount = $smsspill[4];
				$qtrxid = $smsspill[8];
				$simam = $smsspill[19];

				$reqnumber=getStringBetween($msg, 'customer','. Your');
				$comamount=getStringBetween($msg, 'commission of','for transfer');
				$qtrxid=getStringBetween($msg, 'transfer ID','has been');
				$simam=getStringBetween($msg, 'new balance is','TAKA.and');
			}else if ($smsspill[4]=="Postpaid") {
				$reqnumber = $smsspill[12];
				$reqamount = $smsspill[10];
				$qtrxid = $smsspill[2];
				$simam = $smsspill[22];
			}else {
				$reqnumber=getStringBetween($msg, 'to','is');
				$reqamount=getStringBetween($msg, 'recharge','Tk to');
				$qtrxid=getStringBetween($msg, 'number is','Your new');
				$simam=getStringBetween($msg, 'balance is','TAKA.'); 
			}
		} // airtel finish

		// teletalk start
		if($pcode=="TT") {
			$reqnumber = $smsspill[4];
			$reqamount = $smsspill[10];
			$qtrxid = $smsspill[3];
			$simam = $smsspill[16];
		} // teletalk finish 

 
		if ($sendcode=="bKash") {
			$reqnumber=getStringBetween($msg, 'for','.');
			$reqamount=getStringBetween($msg, 'request of Tk','for:');
			$qtrxid=getStringBetween($msg, 'TrxID','at'); 
			$simam=getStringBetween($msg, 'Balance Tk','. TrxID'); 
		} 
  
		if ($sendcode=="NAGAD") {
			$reqnumber=getStringBetween($msg, 'Mobile:','TxnID:');
			$reqamount=getStringBetween($msg, 'Amount: Tk','Mobile:');
			$qtrxid=getStringBetween($msg, 'TxnID:','Balance:'); 
			$simam=getStringBetween($msg, 'Balance: Tk','.'); 
		}
  
		// bKash Start Here Final
		if ($pcode=="BK") {
			$reqnumber=getStringBetween($msg, 'to','successful.');
			$reqamount=getStringBetween($msg, 'Cash In Tk','to');
			$qtrxid=getStringBetween($msg, 'TrxID','at');
			if (empty($qtrxid)) {
				$qtrxid=getStringBetween($msg, 'TrxID',' ');
			}
			$simam=getStringBetween($msg, 'Balance Tk','. TrxID'); 
		}

		if ($pcode=="BKA,BKS" || $pcode=="BKS,BKA" || $pcode=="BKA" || $pcode=="BKS") {
			$reqnumber=getStringBetween($msg, 'to','successful.');
			$sm = "Send Money Tk";
			$co = "Cash Out Tk";
			if (preg_match("/".$sm."/", $msg )) {
				$reqamount=getStringBetween($msg, 'Send Money Tk','to');
			}
			if (preg_match("/".$co."/", $msg )) {
				$reqamount = getStringBetween($msg,'Cash Out Tk','to');
			}
			$qtrxid=getStringBetween($msg, 'TrxID','at');
			if (empty($qtrxid)) {
				$qtrxid=getStringBetween($msg, 'TrxID',' ');
			}
			$simam=getStringBetween($msg, 'Balance Tk','. TrxID'); 
		}
  		// bKash END Here

		// Roket Start Here Final
		if ($pcode=="RK") {
			$reqnumber=getStringBetween($msg, 'Cash-In to A/C:',' Tk');
			$reqamount=getStringBetween($msg, ' Tk',' Comm:');
			$qtrxid=getStringBetween($msg, ' TxnId:',' Date:');
			if (empty($qtrxid)) {
				$qtrxid=getStringBetween($msg, 'TrxID',' ');
			}
			$simam=getStringBetween($msg, 'Your A/C Balance: Tk',' TxnId:'); 
		}


		if ($pcode=="RKA,RKS" || $pcode=="RKS,RKA" || $pcode=="RKA" || $pcode=="RKS") {
			$sm = "transferred to";
			if (preg_match("/".$sm."/", $msg )) {
				$reqamount=getStringBetween($msg, 'Tk','transferred to');
				$reqnumber=getStringBetween($msg, 'to A/C:','Fee:');
			}

			$co = "Cash-Out to";
			if (preg_match("/".$co."/", $msg )) {
				$reqamount = getStringBetween($msg,'Tk','Fee:Tk');
				$reqnumber=getStringBetween($msg, 'Cash-Out to A/C:','Tk');
			}
			$qtrxid=getStringBetween($msg, 'TxnId:','Date:'); 
			$simam=getStringBetween($msg, 'Balance: Tk','TxnId'); 
		}
		// Roket END Here

		// Nagad Start Here Final
		if ($pcode=="NG") {
			$reqnumber=getStringBetween($msg, 'Customer:','TxnID:');
			$reqamount=getStringBetween($msg, 'Amount: Tk','Customer:');
			$qtrxid=getStringBetween($msg, 'TxnID:','Comm:');
			if (empty($qtrxid)) {
				$qtrxid=getStringBetween($msg, 'TrxID',' ');
			}
			$simam=getStringBetween($msg, 'Balance: Tk','.'); 
		}


		if ($pcode=="NGA,NGS" || $pcode=="NGS,NGA" || $pcode=="NGA" || $pcode=="NGS") {
			$sm = "Send Money";
			if (preg_match("/".$sm."/", $msg )) {
				$reqamount=getStringBetween($msg, 'Amount: Tk','Receiver:');
				$reqnumber=getStringBetween($msg, 'Receiver:','Ref:');
				$qtrxid=getStringBetween($msg, 'TxnID:','Fee:'); 
				$simam=getStringBetween($msg, 'Balance: Tk','.');
			}

			$co = "Cash-Out";
			if (preg_match("/".$co."/", $msg )) {
				$reqamount = getStringBetween($msg,'Amount: Tk','Fee:');
				$reqnumber=getStringBetween($msg, 'Uddokta:','TxnID');
				$qtrxid=getStringBetween($msg, 'TxnID:','Amount:'); 
				$simam=getStringBetween($msg, 'Bal: Tk','.'); 
			}
		}
		// Nagad END Here

		$status   = 1;
		$status_r = "Success";
		$stlocal  = 1;
		$statusCom = 2;
		$reqnumber = trim($reqnumber);
		
		$numberlenth=strlen($reqnumber); 
 
		
		if($numberlenth==10) {
			$regexp = "/^[0-9]{10}$/"; 
		} else {
			$regexp = "/^[0-9]{11}$/";
		}

		if (substr($reqnumber, 0,1)!="0") {
			$reqnumber = "0".$reqnumber;
		}

		if (preg_match($regexp, $reqnumber)) {
			$reqnumber = preg_replace( '/[^0-9]/', '', $reqnumber); 
			$numberfind = 'yes';
		} else {
			$numberfind = 'no';
		}
		
		$reqnumber 	= trim($reqnumber);
		$reqamount = trim($reqamount);
		$reqamount = preg_replace('/[^0-9.]/', '', $reqamount);
		$reqamount = floatval($reqamount);
		$reqamount = intval($reqamount);
		$simam = trim($simam);
		
		//$reqnumber = trim($reqnumber);
		//$reqamount = trim($reqamount);
		//$reqamount = (float)preg_replace("/[^0-9.]+/", "", $reqamount);
		//$reqamount = intval(str_replace('.', '', $reqamount));
		//$simam = trim($simam);
		
		//$reqnumber = trim($reqnumber);
		//$reqamount = trim($reqamount);
		//$reqamount = preg_replace('/[^0-9.]/', '', $reqamount);
		//$reqamount = intval(str_replace('.', '', $reqamount));
		//$simam = trim($simam);
		
		//$reqnumber 	= trim($reqnumber); 
		//$reqamount 	= trim($reqamount); 
		//$reqamount 	= intval($reqamount);
		//$simam 		= trim($simam); 
 
		// $reqamount = (float)preg_replace("/[^0-9.]+/", "", $reqamount); 
		// $reqnumber = trim(preg_replace( '/[^0-9]/', '', $reqnumber));  
  		// $simam 	   = rtrim((float)preg_replace("/[^0-9.]+/", "", $simam),'.');
        // dd($reqnumber);
		
  		if ($pcode=="BK" || $pcode=="BKA,BKS" || $pcode=="BKS,BKA" || $pcode=="BKA" || $pcode=="BKS") { 
			$firstPart = strstr($reqnumber, 'X', true);
			$lastPart = substr($reqnumber, strrpos($reqnumber, 'X') + 1);
			$likePattern = $firstPart . '%' . $lastPart;
		}else if($pcode=="NG")
		{
			$firstPart = strstr($reqnumber, '*', true);
			$lastPart = substr($reqnumber, strrpos($reqnumber, '*') + 1);
			$likePattern = $firstPart . '%' . $lastPart;
		}else if($pcode=="RK")
		{
			$firstPart = strstr($reqnumber, '*', true);
			$lastPart = substr($reqnumber, strrpos($reqnumber, '*') + 1);
			$likePattern = $firstPart . '%' . $lastPart;
		}else{
			 $likePattern = '%' . $reqnumber . '%';
		}
		
		if(!empty($reqnumber) && empty($reqamount)) {
			$quryup = "SELECT * FROM `reload_sent_number` WHERE `reload_number` LIKE '$likePattern' AND `status` = 'Waiting' ORDER BY id ASC LIMIT 1"; 
		}elseif(!empty($reqnumber) && !empty($reqamount)){
			$quryup = "SELECT * FROM `reload_sent_number` WHERE `reload_number` LIKE '$likePattern' AND `amount` = '$reqamount' AND `status`='Waiting'  ORDER BY id ASC LIMIT 1"; 
		}
		else {
			$quryup = ""; 
		}
	 
		//while ($result = $querycount->fetch_assoc()) {
		if (!empty($quryup)) {
			$result = $conn->query($quryup)->fetch_array();
			if (!empty($result)) {
				$flexi_id = $result['id']; 
				$ucid = $result['user_id']; 
				$flexi_amount = $result['amount'];  

				$reload_id = $result['id'];
			    $country_id = $result['country_id'];
			    $reload_number = $result['reload_number'];
			    $parents_commission = json_decode($result['parents_commission'],true);
			    $user_id = $result['user_id'];
			    if ($result['reload_code']=='SK') { 
					$qtrxid= getStringBetween($msg, 'Transaction number ',' to recharge');  
			    }
			    $conn->query("UPDATE reload_sent_number SET status = '$status_r',topup_number='$simno',sim_balance='$simam', trxid='$qtrxid', statusCom = '$statusCom',remark='Automatic sent".$result['parents_commission']."' WHERE id = '$reload_id' ");
			    if (count($parents_commission)>0) {
			        foreach ($parents_commission as $key => $value) {
			           // if ($value !== "0.00") {
			                $old_balance = get_user_balance($conn, $key);
			                $new_balance = $old_balance + $value;
			                $conn->query("INSERT INTO user_transaction VALUES (null, '$key', '1', 'TopupCommission','$reload_number','$value','$old_balance','$new_balance', '$curDate')");
			                $conn->query("UPDATE user SET balance = balance+'$value' WHERE id ='$key'");
			           // }
			        }
			    }
			    $flexi_amount = $flexi_amount;
				$user_id = $user_id;
				$reload_number = $reload_number;
				$reload_id = $reload_id;
				$country_id = $country_id;
				$simno = $simno;
			}
		}
		//}	
	}
	// recharge message execute end

  	//flash message here
  	if (!empty($slid) && !empty($msg) && $sendcode=="FlashMessage") {

  		$quryup = "SELECT * FROM `reload_sent_number` WHERE `id` = '$slid'  AND `status`='Waiting'";

  		$querycount = $conn->query($quryup);
  		  $parents_commission =array();
		if ($querycount->num_rows>0) {
			while ($result = $querycount->fetch_assoc()) {
				$flexi_id = $result['id']; 
				$ucid = $result['user_id']; 
				$flexi_amount = $result['amount'];  
				$reload_id = $result['id'];
	            $country_id = $result['country_id'];
	            $reload_number = $result['reload_number']; 
	            $user_id = $result['user_id'];
	             $parents_commission = json_decode($result['parents_commission'],true);
	        }
	        $flexi_amount = $flexi_amount;
			$user_id = $user_id;
			$reload_number = $reload_number;
			$reload_id = $reload_id;
			$country_id = $country_id;
			$simno = $simno;
			if ((strpos($msg,'Invalid bKash Account No.') !== false) OR
				(strpos($msg,'The feature is not supported for this product.') !== false) OR 
				(strpos($msg,'Payee limit exceeded.') !== false) OR
				(strpos($msg,'Insufficient funds.') !== false) OR
                (strpos($msg,'Transaction is not possible due to Limit') !== false) OR 
                (strpos($msg,'The destination account is in a state which prohibits the execution of the transaction') !== false) OR 
	

				(strpos($msg,'Invalid account type for cash in.') !== false) OR 
				(strpos($msg,'You do not have a registered Nagad account. Please visit nearest Nagad uddokta or call 16167.') !== false) OR
				(strpos($msg,'Daily usage limit exceeded. Please try again later.') !== false) OR
				(strpos($msg,'Insufficient balance. Please check and try again later.') !== false) OR
				(strpos($msg,'Transaction not within limit. Please check and try again. Call 16167 for more information.') !== false) 
				) 
			{
				


					

					if((strpos($msg,'Invalid bKash Account No.') !== false) )
					{
						$custmsg ='দুঃখিত, এই নাম্বারে বিকাশ একাউন্ট নেই।'; //ye user ko msg show hoga
					}

					if((strpos($msg,'The feature is not supported for this product.') !== false) )
					{
						$custmsg ='দুঃখিত, এই নাম্বারটি এজেন্ট'; //ye user ko msg show hoga
					} 

					if((strpos($msg,'Payee limit exceeded.') !== false) )
					{
						$custmsg ='দুঃখিত, আপনার এই নাম্বারে লিমিট নেই।'; //ye user ko msg show hoga
					} 

					if((strpos($msg,'Insufficient funds.') !== false) )
					{
						$custmsg ='দুঃখিত, এজেন্টে ব্যালেন্স নেই।'; //ye user ko msg show hoga
					} 

					if((strpos($msg,'Transaction is not possible due to Limit') !== false) )
					{
						$custmsg ='দুঃখিত, এই নাম্বারে লিমিট নেই।'; //ye user ko msg show hoga
					} 

					if((strpos($msg,'The destination account is in a state which prohibits the execution of the transaction') !== false) )
					{
						$custmsg ='দুঃখিত, এই নাম্বারে লেনদেন করা সম্ভব নয়।'; //ye user ko msg show hoga
					} 





					if((strpos($msg,'Invalid account type for cash in.') !== false) )
					{
						$custmsg ='দুঃখিত, এই নাম্বারটি এজেন্ট'; //ye user ko msg show hoga
					} 

					if((strpos($msg,'You do not have a registered Nagad account. Please visit nearest Nagad uddokta or call 16167.') !== false) )
					{
						$custmsg ='দুঃখিত, এই নাম্বারে নগত একাউন্ট নেই।'; //ye user ko msg show hoga
					}

					if((strpos($msg,'Daily usage limit exceeded. Please try again later.') !== false) )
					{
						$custmsg ='দৈনিক ব্যবহারের সীমা অতিক্রম করেছে। অনুগ্রহ করে পরে আবার চেষ্টা করুন।'; //ye user ko msg show hoga
					}

					if((strpos($msg,'Insufficient balance. Please check and try again later.') !== false) )
					{
						$custmsg ='দুঃখিত, এজেন্টে ব্যালেন্স নেই।'; //ye user ko msg show hoga
					}

					if((strpos($msg,'Transaction not within limit. Please check and try again. Call 16167 for more information.') !== false) )
					{
						$custmsg ='দুঃখিত, এই নাম্বারে লিমিট নেই।'; //ye user ko msg show hoga
					}
				 $conn->query("UPDATE reload_sent_number SET status = 'Failed' ,remark='Failed m f android ".count($parents_commission)."',trxid='$custmsg' WHERE id = '$reload_id' ");
				 $pccount= count($parents_commission);
                if (count($parents_commission)>0){
                	 $conn->query("UPDATE reload_sent_number SET remark='Failed m f android inside if',trxid='$custmsg' WHERE id = '$reload_id' ");
                	 $txt = '';
                	 $i=0;
                   foreach ($parents_commission as $key => $value) {
                   	  $old_balance = get_user_balance($conn, $key);
                       $new_balance = ($old_balance + $flexi_amount);
                       $conn->query("INSERT INTO user_transaction VALUES (null, '$key', '$key', 'ReloadRefund','$reload_number','$flexi_amount','$old_balance','$new_balance', '$curDate')");
                      $conn->query("UPDATE user SET balance = balance+'$flexi_amount' WHERE id ='$key'");
                      
                        $conn->query("UPDATE reload_sent_number SET remark='Automatic Refund' WHERE id = '$reload_id' ");

                      
                   }
                   
                }
			}
		}
			
  	}
 
	$sql_q = "INSERT INTO sim_message_report VALUES(NULL,'$msg','$user_id','$flexi_amount','$reload_number','$reload_id','$curDate','$country_id','$simno')"; 
	$sql_q = $conn->query($sql_q);


	$simbalupdate = "UPDATE  `siminfo` SET `status` =  '1', `time` =  '$time', `date` =  '$create_date' WHERE  `siminfo`.`OwnNumber` ='$simno' AND Operator='$operator'"; 
	$conn->query($simbalupdate);

	if(!empty($simam)) {
		$simbalupdate2 = "UPDATE  `siminfo` SET `Balance` =  '$simam' WHERE  `siminfo`.`OwnNumber` ='$simno' AND Operator='$operator'"; 
		$sql = $conn->query($simbalupdate2); 
	}

	if($sql_q) {
		$insert = '1';
		$inmsg = "Message Is Successfuly Insert";
		$allset = $reqnumber;
	} else {
		$insert = '1';
		$inmsg = "not insert";
	}

	$response = array(
		"msg" => $inmsg,
		"insert" => $insert,
		"position" => $status, 
		"check" => $simno, 
		"simam" => $simam, 
		"status" => 1
	);

} else {

	$response = array("msg" => 'empty not work',"status" => 2);

}

echo json_encode($response);  

function getStringBetween($str,$from="",$to=""){
	$sub = substr($str, strpos($str,$from)+strlen($from),strlen($str));
	if(!empty($to)){
		return substr($sub,0,strpos($sub,$to));
	}else {
		return substr($sub,strpos($sub,$to));
	}
}

function gp_new_message_method($message, $amount_start_char, $amount_end_char, $number_start_char, $number_end_char, $trx_start_char, $trx_end_char, $balance_start_char, $balance_end_char) {
    $result = array(
        'amount' => null,
        'number' => null,
        'trxid' => null,
        'balance' => null
    );

    // Extract amount
    $amount_start = strpos($message, $amount_start_char) + strlen($amount_start_char);
    $amount_end = strpos($message, $amount_end_char, $amount_start);
    if ($amount_start !== false && $amount_end !== false) {
        $amt = substr($message, $amount_start, $amount_end - $amount_start);
        $result['amount'] = round(floatval($amt));
    }

    // Extract number
    $number_start = strpos($message, $number_start_char) + strlen($number_start_char);
    $number_end = strpos($message, $number_end_char, $number_start);
    if ($number_start !== false && $number_end !== false) {
        $number = substr($message, $number_start, $number_end - $number_start);
        if (strpos($number, '0') !== 0) {
            $number = '0' . $number;
        }
        $result['number'] = $number;
    }

    // Extract transaction ID
    $trx_start = strpos($message, $trx_start_char) + strlen($trx_start_char);
    $trx_end = strpos($message, $trx_end_char, $trx_start);
   
    if ($trx_start !== false && $trx_end !== false) {
        $result['trxid'] = substr($message, $trx_start, $trx_end - $trx_start);
    }

    // Extract balance
    $balance_start = strpos($message, $balance_start_char) + strlen($balance_start_char);
    $balance_end = strpos($message, $balance_end_char, $balance_start);
    if ($balance_start !== false && $balance_end !== false) {
        $result['balance'] = substr($message, $balance_start, $balance_end - $balance_start);
    }

    return $result;
}
?>