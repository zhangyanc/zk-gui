$(function () {
    $("ul").delegate("li a", "click", function (e) {
        e.preventDefault();
        var href = this.id;
        $.get("/info" + href, function (result) {
            $("ul.breadcrumb li:gt(0)").remove();

            var isRoot = href == '/';
            if (!isRoot) {
                $.each(href.split("/"), function (i, v) {
                    if (v != "") {
                        $(".breadcrumb").append("<li><a href='#' id='" + href.substr(0, href.indexOf(v) + v.length) + "'><span>" + v + "</span></a></li>");
                    }
                });
            }

            $("ul.children li:gt(0)").remove();
            $.each(result.children, function (i, v) {
                $("ul.children").append("<li><a href='#' id='"+(isRoot ? href + v : href + '/' + v)+"'><span>" + v + "</span></a></li>");
            });

            $("tr").find("td:last").each(function () {
                var $td = $(this);
                $td.text(result.stat[$td.attr("id")]);
            });
            $("#data").append(result.data);
        })
    });
    $(".breadcrumb li:first a").trigger("click");
});