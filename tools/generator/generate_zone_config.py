import os
import re
import subprocess
import platform

# C# → Java 타입 매핑
CS_TO_JAVA_TYPE = {
    'string': 'String',
    'int':    'Integer',
    'float':  'Float',
    'double': 'Double',
    'long':   'Long',
    'bool':   'Boolean',
}

def open_file_location(file_path):
    """생성된 파일이 있는 폴더를 운영체제의 기본 파일 탐색기로 열기"""
    try:
        abs_path = os.path.abspath(file_path)
        folder_path = os.path.dirname(abs_path)

        system = platform.system()
        if system == "Windows":
            subprocess.run(f'explorer /select,"{abs_path}"', shell=True)
        elif system == "Darwin":
            subprocess.run(['open', '-R', abs_path], check=True)
        elif system == "Linux":
            subprocess.run(['xdg-open', folder_path], check=True)

        print(f"Opened file location: {folder_path}")
    except Exception as e:
        print(f"Failed to open file location: {e}")


def cs_type_to_java(cs_type):
    """C# 타입 → Java 타입 변환 (primitive + complex)"""
    if cs_type in CS_TO_JAVA_TYPE:
        return CS_TO_JAVA_TYPE[cs_type]
    # List<T>
    list_match = re.match(r'List<(\w+)>', cs_type)
    if list_match:
        return f'List<{cs_type_to_java(list_match.group(1))}>'
    # enum (E 접두사)
    if cs_type.startswith('E'):
        return cs_type
    # Request/Response는 그대로
    if cs_type.endswith('Request') or cs_type.endswith('Response'):
        return cs_type
    # 그 외 복합 타입 → Dto suffix
    return f'{cs_type}Dto'

def parse_server_fields_from_csharp(cs_file_path, class_name):
    """C# 소스에서 class_name 클래스의 // [server] 마커가 붙은 public 필드를 추출"""
    with open(cs_file_path, 'r', encoding='utf-8') as f:
        source = f.read()

    # class_name 클래스 블록 추출
    pattern = rf'public class {re.escape(class_name)}\s*\{{(.*?)\n\}}'
    match = re.search(pattern, source, re.DOTALL)
    if not match:
        raise ValueError(f"Class '{class_name}' not found in {cs_file_path}")

    class_body = match.group(1)

    # public <type> <name> ... // [server] — 모든 타입 처리
    field_pattern = re.compile(
        r'public\s+([\w<>]+)\s+(\w+)[^;]*;\s*//\s*\[server\]',
        re.MULTILINE
    )

    fields = []
    for m in field_pattern.finditer(class_body):
        cs_type, field_name = m.group(1), m.group(2)
        java_type = cs_type_to_java(cs_type)
        fields.append({'name': field_name, 'type': java_type})

    return fields


def generate_zone_config_dto(cs_source_path, output_dir, package_name):
    """C# ZoneStageConfig에서 [server] 마커 필드를 읽어 ZoneConfigData.java 생성"""

    server_fields = parse_server_fields_from_csharp(cs_source_path, 'ZoneStageConfig')
    if not server_fields:
        raise ValueError("No [server] fields found in ZoneStageConfig. Check // [server] markers in DataTableZone.cs")

    java_class_name = "ZoneConfigData"

    java_code  = f"package {package_name};\n\n"
    java_code += "import lombok.AllArgsConstructor;\n"
    java_code += "import lombok.Builder;\n"
    java_code += "import lombok.Data;\n"
    java_code += "import lombok.NoArgsConstructor;\n"
    java_code += "\n"
    java_code += "/**\n"
    java_code += f" * {java_class_name}\n"
    java_code += " * Auto-generated from Unity C# ZoneStageConfig class (server-required fields only)\n"
    java_code += " */\n"
    java_code += "@Data\n"
    java_code += "@NoArgsConstructor\n"
    java_code += "@Builder\n"
    java_code += "@AllArgsConstructor\n"
    java_code += f"public class {java_class_name} {{\n"

    for field in server_fields:
        java_code += f"    private {field['type']} {field['name']};\n"

    java_code += "}\n"

    output_file_path = os.path.join(output_dir, f"{java_class_name}.java")
    os.makedirs(output_dir, exist_ok=True)

    with open(output_file_path, 'w', encoding='utf-8') as file:
        file.write(java_code)

    print(f"Generated: {output_file_path}")
    print(f"  Fields: {', '.join(f['name'] for f in server_fields)}")

    return output_file_path


if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))

    # C# 소스 경로 (클라 프로젝트 기준 상대 경로)
    cs_source_path = os.path.join(
        script_dir,
        r"../../../thefirst_client_unity/Assets/Scripts/System/Data/DataTableZone.cs"
    )

    output_dir   = os.path.join(script_dir, r"../../src/main/java/com/bk/sbs/dto")
    package_name = "com.bk.sbs.dto"

    print("Generating ZoneConfigData (parsed from C# // [server] markers)")
    print("="*55)
    print(f"Source: {os.path.abspath(cs_source_path)}")

    output_file = generate_zone_config_dto(cs_source_path, output_dir, package_name)

    if output_file:
        open_file_location(output_file)
