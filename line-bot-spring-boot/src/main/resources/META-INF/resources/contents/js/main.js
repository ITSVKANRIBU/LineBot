// 共通化
function includeHtml(url) {
	$.ajax({
		url : url, // 読み込むHTMLファイル
		cache : false,
		async : false,
		dataType : 'html',
		success : function(html) {
			document.write(html);
		}
	});
}

//チェック機能
function select_checked(id) {
	select = document.getElementById(id).options;
	data = $('#' + id).attr("data");
	select[0].selected = true;
	for (let i = 0; i < select.length; i++) {
		if (select[i].value === data) {
			select[i].selected = true;
			break;
		}
	}
}

$(function() {
	$('tbody tr[data-href]').addClass('clickable').click(function() {
		window.location = $(this).attr('data-href');
	}).find('a').hover(function() {
		$(this).parents('tr').unbind('click');
	}, function() {
		$(this).parents('tr').click(function() {
			window.location = $(this).attr('data-href');
		});
	});


	$('#listbutton').click(function() {
		name = $('#name').val();
		if(name){
			href = '/Insider?button=list&name=' + name;
			window.location = href;
		}else{
			$('#name')[0].setCustomValidity('入力した投稿者のお題を表示します');
			$('#name')[0].reportValidity();
			window.setTimeout("$('#name')[0].setCustomValidity('')", 2000)
		}
	});
			
			
			
			
			
			

	
	// ツールチップ有効
	var distance = 20;
	var time = 200;
	var hideDelay = 200;
	var hideDelayTimer = null;
	var beingShown = false;
	var shown = false;
	var trigger = $('.trigger', this);
	var info = $('.popup', this).css('opacity', 0);

	$('.trigger').mouseover(function () {
		console.log('表示=' + shown + ' 途中=' + beingShown);
		if (hideDelayTimer) clearTimeout(hideDelayTimer);
		if (beingShown || shown) {
			return;
			} else {
				callWiki($(this).text() ,$(this).attr('id'));
				beingShown = true;
				var leftValue = $(this).offset().left;
				var topValue = $(this).offset().top;
				info.css({
					top: topValue,
					left: leftValue,
					display: 'block'
				}).animate({
					top: '+=' + distance + 'px',
					left: '+=' + distance + 'px',
					opacity: 1
				}, time, 'swing', function() {
					beingShown = false;
					shown = true;
				});
			}
			return false;
		});
			
			
		$('.trigger').mouseout(function () {
			hidePop();
		});
	 
		$('div.popup').mouseout(function () {
			hidePop();
		});
		 
		function hidePop() {
			var flag = false; //判定用フラグ準備
			jQuery(":hover").each(function () { //カーソル位置の全要素を走査
				var idName = $(this).attr('id');
				if (idName == 'popImg' || idName == 'popData' ) {
					flag = true;
				}
			});
			if (flag) { //flagがtrueでなければ「layer」から外れている
				return;
			}
			
			beingShown = false;
			shown = false;
			
			// 実行
			if (hideDelayTimer) clearTimeout(hideDelayTimer);
			hideDelayTimer = setTimeout(function () {
				hideDelayTimer = null;
				info.animate({
					top: '-=' + distance + 'px',
					opacity: 0
				}, time, 'swing', function () {
					info.css('display', 'none');
				});
			}, hideDelay);
			return false;
		}
		
		function callWiki(title,labelId) {
			// 非表示
			$('#popData').css('display', 'none');
			$('#popImg').css('display', 'none');
			
			
			var dataId = labelId.replace('label','');
			
			$.ajax({
				url: 'https://ja.wikipedia.org/w/api.php?format=xml&action=query&prop=revisions&rvprop=content&titles=' + title + '&origin=*&rvparse',
				type: 'get',
				timeout: 7000,
			})
			.done(function(data) {
				// 通信成功時の処理を記述
				var myHTML = $.parseXML($(data).find('rev').text());
				//console.log(myHTML);
				$(myHTML).find("p").each(function() {
					//console.log($(this));
					// 置換しながら設定
					$('#popData').html($(this).html().replace(/=\"\//g,'=\"https://ja.wikipedia.org/'));
					// 表示
					$('#popData').css('display', 'inline-block');
					return false;
				});
			})
			.fail(function() {
				console.log('err');
			});
			
			
			// &piprop=originalを外せばサムネイルになる
			$.ajax({
				url: 'https://ja.wikipedia.org/w/api.php?format=xml&action=query&prop=pageimages&piprop=original&titles=' + title + '&origin=*',
				type: 'get',
				timeout: 2000,
			})
			.done(function(imgData) {
				// 通信成功時の処理を記述
				
				$(imgData).find('page').each(function(){
					// console.log($(this).attr('source'));
					var pageid =  $(this).attr('pageid');
					
					if(pageid){
						console.log(pageid);
						$('.popup a').attr('href','https://ja.wikipedia.org/?curid=' + pageid);
						$('#link' + dataId).attr('href','https://ja.wikipedia.org/?curid=' + pageid);
						$('#link'  + dataId + ' img').css('visibility', 'visible');
					}
					var imgUrl = $(this).find('original').attr('source');
					if(imgUrl){
						$('#popImg').attr('src',imgUrl);
						// 表示
						$('#popImg').css('display', 'inline-block');
					}
					return false;
				});
			})
			.fail(function() {
				console.log('err');
			});
			
		}
});	