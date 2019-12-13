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