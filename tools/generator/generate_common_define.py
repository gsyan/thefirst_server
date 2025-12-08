import re
import os
import subprocess
import platform

def open_file_location(file_path):
    """생성된 파일이 있는 폴더를 운영체제의 기본 파일 탐색기로 열기"""
    try:
        # 절대 경로로 변환
        abs_path = os.path.abspath(file_path)
        folder_path = os.path.dirname(abs_path)

        system = platform.system()
        if system == "Windows":
            # 윈도우: 파일을 선택한 상태로 탐색기 열기
            subprocess.run(f'explorer /select,"{abs_path}"', shell=True)
        elif system == "Darwin":  # macOS
            subprocess.run(['open', '-R', abs_path], check=True)
        elif system == "Linux":
            subprocess.run(['xdg-open', folder_path], check=True)
        else:
            print(f"Unsupported operating system: {system}")
            return

        print(f"Opened file location: {folder_path}")
    except Exception as e:
        print(f"Failed to open file location: {e}")
        print(f"File location: {os.path.abspath(file_path)}")

def generate_java_enum_from_csharp(csharp_file_path, output_dir, package_name):
    # C# 파일 읽기
    with open(csharp_file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    # 모든 enum 찾기 (E로 시작하는 enum만)
    enum_pattern = r'public\s+enum\s+(E\w+)\s*\{([^}]+)\}'
    enum_matches = re.finditer(enum_pattern, content, re.DOTALL)

    generated_files = []

    for enum_match in enum_matches:
        enum_name = enum_match.group(1)  # EFormationType
        enum_body = enum_match.group(2)

        # enum 값들 추출 (주석 제거)
        values = []
        for line in enum_body.split('\n'):
            line = line.strip()
            if line and not line.startswith('//'):
                # Linear,      // 일렬 배치 → Linear
                # None = 0,    → (None, 0)
                value = re.sub(r'\s*//.*', '', line).strip(' ,')
                if value:
                    # = 기호로 분리하여 (이름, 값) 튜플로 저장
                    if '=' in value:
                        parts = value.split('=')
                        name = parts[0].strip()
                        num = parts[1].strip()
                        values.append((name, num))
                    else:
                        values.append((value, None))

        # Java enum 코드 생성
        java_code = f"package {package_name};\n\n"

        # 값이 있는 enum인지 확인 (import 문 추가용)
        has_value = any(num is not None for _, num in values)

        if has_value:
            java_code += "import com.fasterxml.jackson.annotation.JsonCreator;\n"
            java_code += "import com.fasterxml.jackson.annotation.JsonValue;\n\n"

        java_code += "/**\n"
        java_code += f" * {enum_name}\n"
        java_code += f" * Auto-generated from Unity C# {enum_name} enum\n"
        java_code += " */\n"
        java_code += f"public enum {enum_name} {{\n"

        # enum 값 정의
        for i, value in enumerate(values):
            name, num = value
            if i == len(values) - 1:
                if num is not None:
                    java_code += f"    {name}({num});\n\n"
                elif has_value:
                    java_code += f"    {name}({i});\n\n"
                else:
                    java_code += f"    {name};\n\n"
            else:
                if num is not None:
                    java_code += f"    {name}({num}),\n"
                elif has_value:
                    java_code += f"    {name}({i}),\n"
                else:
                    java_code += f"    {name},\n"

        # 값이 있는 enum인 경우 value 필드와 메서드 추가
        if has_value:
            java_code += "    private final int value;\n\n"
            java_code += f"    {enum_name}(int value) {{\n"
            java_code += "        this.value = value;\n"
            java_code += "    }\n\n"
            java_code += "    @JsonValue\n"
            java_code += "    public int getValue() {\n"
            java_code += "        return value;\n"
            java_code += "    }\n\n"
            java_code += "    @JsonCreator\n"
            java_code += f"    public static {enum_name} fromValue(int value) {{\n"
            java_code += f"        for ({enum_name} type : values()) {{\n"
            java_code += "            if (type.value == value) return type;\n"
            java_code += "        }\n"
            # 첫 번째 값을 기본값으로 반환
            first_value_name = values[0][0]
            java_code += f"        return {first_value_name};\n"
            java_code += "    }\n"

        java_code += "}"

        # 출력 파일 경로
        output_file_path = os.path.join(output_dir, f"{enum_name}.java")

        # 출력 디렉토리 생성
        os.makedirs(output_dir, exist_ok=True)

        # Java 파일 쓰기
        with open(output_file_path, 'w', encoding='utf-8') as file:
            file.write(java_code)

        generated_files.append(output_file_path)
        print(f"Java enum file generated: {output_file_path}")
        print(f"  - Values: {', '.join([name for name, _ in values])}")

    if generated_files:
        # 첫 번째 생성된 파일 위치 열기
        open_file_location(generated_files[0])
    else:
        print("No enum found in C# file")

if __name__ == "__main__":
    csharp_file_path = r"../../../thefirst_client_unity/Assets/Scripts/System/CommonDefine.cs"
    output_dir = r"../../src/main/java/com/bk/sbs/enums"
    package_name = "com.bk.sbs.enums"
    generate_java_enum_from_csharp(csharp_file_path, output_dir, package_name)