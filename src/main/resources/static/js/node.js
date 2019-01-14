$(function () {
    var currentNode;

    var notyOptions = {
        layout:"center",
        type:"error",
        closeOnSelfClick: false,
        closeButton: true,
        timeout: 8000
    };

    function notyErr(error) {
        noty($.extend(notyOptions, {text: error}))
    }

    function getNodeInfo(href) {
        $.get("/info" + href, function (result) {
            currentNode = href;
            $(".currentNode").text(href);
            var $breadcrumb = $("ul.breadcrumb");
            $breadcrumb.empty().append("<li><a href='#' id='/'><span>/</span></a></li>");

            var isRoot = href == '/';
            if (!isRoot) {
                $.each(href.split("/"), function (i, v) {
                    if (v != "") {
                        $breadcrumb.append("<li><a href='#' id='" + href.substr(0, href.indexOf(v) + v.length) + "'><span>" + v + "</span></a></li>");
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
            $("#data").text(result.data);
        })
    }

    $(".breadcrumb,.children").delegate("li a", "click", function (e) {
        e.preventDefault();
        getNodeInfo(this.id);
    });

    getNodeInfo("/");

    var $createModal = $("#createModal");

    $createModal.on("hidden.bs.modal", function () {
        $("input[name=child], textarea[name=childData]").val("");
        $("input[name=ephemeral], input[name=sequential]").attr("checked", false);
    });

    $createModal.find(".confirm").click(function (e) {
        e.preventDefault();
        $.ajax({
            url: "/create",
            type: "post",
            dataType: "json",
            data: {
                node: currentNode + (currentNode == "/" ? "" : "/") + $("input[name=child]").val(),
                data: $("textarea[name=childData]").val(),
                ephemeral: $("input[name=ephemeral]").is(":checked"),
                sequential: $("input[name=sequential]").is(":checked")
            },
            success: function (result) {
                if (result.error) {
                    notyErr(result.error);
                } else {
                    $createModal.modal("hide");
                    getNodeInfo(currentNode);
                }
            }
        })
    });
});