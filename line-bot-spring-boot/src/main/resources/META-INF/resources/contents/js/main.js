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
	var trigger = $('.trigger', this);

	$('.trigger').mouseover(function () {
		if (hideDelayTimer) clearTimeout(hideDelayTimer);
		if (beingShown) {
			return;
			} else {
				callWiki($(this).text() ,$(this).attr('id'));
				beingShown = true;
				var leftValue = $(this).offset().left;
				var topValue = $(this).offset().top;
				beingShown = false;
			}
			return false;
		});
	
	function callWiki(title,labelId) {
		$('.balloon').remove();
		
		$.ajax({
			url: 'https://ja.wikipedia.org/api/rest_v1/page/summary/' + title,
			type: 'get',
			timeout: 5000,
		})
		.done(function(data) {
			// 通信成功時の処理を記述
			var pageid = data.pageid;
			var htmlText = data.extract_html;
			var imgsrc = null;
			var imgTateFlg = false;
			if(data.originalimage){
				var imgsrc = data.originalimage['source'];
				if(data.originalimage['width'] < data.originalimage['height']){
					imgTateFlg= true;
				}
				console.log(imgTateFlg);
				console.log(imgsrc);
			}
			console.log(pageid);
			console.log(htmlText);
			
			var addImg = '';
			if(imgsrc){
				if(imgTateFlg){
					addImg = '<img style="float: left; max-width: 200px; max-height: 500px;"  src="' + imgsrc +'">';
				}else{
					addImg = '<img style="max-width: 400px; max-height: 300px;"  src="' + imgsrc +'">';
				}
			}
			var addDiv = '<div class="balloon"><a href="'+ 'https://ja.wikipedia.org/?curid=' + pageid + '">' + addImg + '<div>'+ htmlText + '</div></div></a>';
			$('.balloon').remove();
			//要素追加
			$('#' + labelId).after(addDiv);
			
		})
		.fail(function() {
			console.log('err');
		});
	
	}
	
});	