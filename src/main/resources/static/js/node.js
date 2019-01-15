$(function () {
    getNodeInfo("/");

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

    function getNodeInfo(node) {
        $.get("/info" + node, function (result) {
            if (result.error) {
                notyErr(result.error);
            } else {
                fill(node, result);
            }
        })
    }

    function fill(node, result) {
        $(".currentNode").text(currentNode = node);
        var $breadcrumb = $("ul.breadcrumb");
        $breadcrumb.empty().append("<li><a href='#' id='/'><span>/</span></a></li>");

        var isRoot = node == '/';
        if (!isRoot) {
            $.each(node.split("/"), function (i, v) {
                if (v != "") {
                    $breadcrumb.append("<li><a href='#' id='" +
                        node.substr(0, node.indexOf(v) + v.length) + "'><span>" + v + "</span></a></li>");
                }
            });
        }
        $("ul.children li:gt(0)").remove();
        $.each(result.children, function (i, v) {
            $("ul.children").append("<li><a href='#' id='" +
                (isRoot ? node + v : node + '/' + v) + "'><span>" + v + "</span></a></li>");
        });

        $("tr").find("td:last").each(function() {
            var $td = $(this);
            $td.text(result.stat[$td.attr("id")]);
        });
        $("#data").text(result.data);
    }

    $(".breadcrumb,.children").delegate("li a", "click", function (e) {
        e.preventDefault();
        getNodeInfo(this.id);
    });

    function post(url, data, $model, node) {
        $.post(url, data, function (result) {
            if (result.error) {
                notyErr(result.error);
            } else {
                $model.modal("hide");
                fill(node, result);
            }
        }, "json");
    }

    var $createModal = $("#createModal"),
        $editDataModal = $("#editDataModal"),
        $deleteModal = $("#deleteModal");

    $createModal.on("hidden.bs.modal", function () {
        $("input[name=child], textarea[name=childData]").val("");
        $("input[name=ephemeral], input[name=sequential]").attr("checked", false);
    });
    $createModal.find(".confirm").click(function (e) {
        e.preventDefault();
        post("/create", {
            node: currentNode + (currentNode == "/" ? "" : "/") + $("input[name=child]").val(),
            data: $("textarea[name=childData]").val(),
            ephemeral: $("input[name=ephemeral]").is(":checked"),
            sequential: $("input[name=sequential]").is(":checked")
        }, $createModal, currentNode)
    });

    $editDataModal.on("show.bs.modal", function () {
        $("textarea[name=editData]").val($("#data").text());
    });
    $editDataModal.find(".confirm").click(function (e) {
        e.preventDefault();
        post("/setData", {
            node: currentNode,
            data: $("textarea[name=editData]").val()
        }, $editDataModal, currentNode);
    });

    $deleteModal.find(".confirm").click(function (e) {
        e.preventDefault();
        var parent = currentNode.substring(0, currentNode.lastIndexOf("/"));
        if (parent == "") {
            parent = "/";
        }
        post("/delete", {node: currentNode}, $deleteModal, parent);
    });
});